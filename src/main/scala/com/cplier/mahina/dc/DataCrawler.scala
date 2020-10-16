package com.cplier.mahina.dc

import java.time.Instant

import akka.actor._
import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.model._
import akka.http.scaladsl.{Http, HttpExt}
import akka.util.ByteString
import com.cplier.mahina.audit._
import com.cplier.mahina.rest.ServiceProtocol._
import com.cplier.mahina.rest._
import spray.json._

import scala.concurrent.Future

object DataCrawler {
  import DataSyncWorker._

  // 全量同步
  final case class FullDose(uri: String)
  object FullDose {
    implicit val toJson: RootJsonFormat[FullDose] = jsonFormat1(FullDose.apply)
  }
  // 增量同步
  final case class DeltaDose(uri: String, keys: Set[String])
  object DeltaDose {
    implicit val toJson: RootJsonFormat[DeltaDose] = jsonFormat2(DeltaDose.apply)
  }

  final case class RespMod(data: String)
  final case class Isomorphism(data: Set[IdleBullet])
}

class DataCrawler(mcache: ActorRef, syncRegion: ActorRef) extends Actor with ActorLogging with ApiResponseJsonProtocol {
  import AuditReceptionist._
  import DataCrawler._
  import DataSyncWorker._
  import DistributedAudit._
  import akka.pattern.pipe
  import context.dispatcher

  implicit val system: ActorSystem = context.system
  val http: HttpExt                = Http(system)

  var replyTo: ActorRef   = _
  var remoteUri: String   = _
  var keyOps: Set[String] = Set()

  override def receive: Receive = {
    case FullDose(uri) =>
      replyTo = sender()
      remoteUri = uri
      http.singleRequest(HttpRequest(uri = Uri(uri).withPath(Path / "replica"))).pipeTo(self)
    case DeltaDose(uri, keys) =>
      replyTo = sender()
      remoteUri = uri
      keyOps = keys
      http.singleRequest(HttpRequest(uri = Uri(uri).withPath(Path / "replica"))).pipeTo(self)
    case HttpResponse(StatusCodes.OK, headers, entity, _) =>
      context become handleResponse(replyTo, remoteUri, keyOps, headers)
      fetchResp(entity).map(d => RespMod(d)).pipeTo(self)
    case resp @ HttpResponse(code, _, _, _) =>
      log.info("Request failed, response code: " + code)
      resp.discardEntityBytes()
      replyTo ! EmptyResult
      self ! PoisonPill
  }

  def handleResponse(replyTo: ActorRef, uri: String, keyOps: Set[String], header: Seq[HttpHeader]): Receive = {
    case RespMod(data) =>
      log.debug(s"Header: $header")
      log.debug(s"Request uri: $uri")
      log.debug(s"Got response, body: ${data.parseJson.compactPrint}")
      log.debug(s"Filtering keys: $keyOps")
      val respResult = data.parseJson.convertTo[ApiResponse[KeysResponse]]
      // destination records
      val desRecords = respResult.response
        .map(kr => kr.records)
        ./:(Map[String, Instant]())(_ ++ _)
        .filter(f => if (keyOps.nonEmpty) keyOps.contains(f._1) else true)
      if (desRecords.isEmpty) {
        replyTo ! EmptyResult
        self ! PoisonPill
      } else {
        context become compareKeySequence(replyTo, uri, desRecords)
        context.actorOf(Props(new DistributedAudit(mcache))) ! GetRecordMsg
      }
    case _ =>
      replyTo ! EmptyResult
      self ! PoisonPill
  }

  def compareKeySequence(replyTo: ActorRef, uri: String, desRecords: Map[String, Instant]): Receive = {
    case RecordMsg(localRecords) =>
      // which keys should be update or delete
      val isomorphism = diffWithInstant(localRecords, desRecords)
      context become waitAllShardingComplete(replyTo)
      syncRegion ! EntityEnvelope(EntityEnvelope.Identity, SyncInit(isomorphism.data))
      isomorphism.data.foreach { k =>
        syncRegion ! EntityEnvelope(k.key, SyncOne(uri, k.key, k.instant, k.save, k.delete))
      }
      self ! SyncAllDone
    case _ =>
      replyTo ! EmptyResult
      self ! PoisonPill
  }

  def waitAllShardingComplete(replyTo: ActorRef): Receive = {
    case SyncAllDone =>
      // we do not wait for the all sync complete due to the complicated network environment.
      replyTo ! FullResult("Done. but the sync worker still holding in the background")
      self ! PoisonPill
  }

  /**
    * compare the timestamp via local key and remote
    */
  def diffWithInstant(local: Map[String, Instant], remote: Map[String, Instant]): Isomorphism = {
    var tmp     = Set[IdleBullet]()
    val allKeys = local.keySet ++ remote.keySet
    for (k <- allKeys) {
      if (local.contains(k)) {
        val localInstant = local(k)
        if (remote.contains(k)) {
          // both contains, compare timestamp
          val remoteInstant = remote(k)
          if (localInstant.isBefore(remoteInstant)) {
            // local is elder than remote
            tmp += IdleBullet(k, remoteInstant, save = true, delete = false)
          }
        } else {
          // remote not contain key, but local found, mark as `delete`
          tmp += IdleBullet(k, local(k), save = false, delete = true)
        }
      } else {
        // if local not contain, mark as `add`
        tmp += IdleBullet(k, remote(k), save = true, delete = false)
      }
    }
    Isomorphism(tmp)
  }

  def fetchResp(entity: ResponseEntity): Future[String] =
    entity.dataBytes.runFold(ByteString(""))(_ ++ _).map(_.utf8String)
}
