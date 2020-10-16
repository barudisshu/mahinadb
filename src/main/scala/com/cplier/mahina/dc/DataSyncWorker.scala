package com.cplier.mahina.dc

import java.time.Instant

import akka.actor._
import akka.cluster.sharding.ClusterSharding
import akka.http.scaladsl.model.Uri.Path
import com.cplier.mahina.service._
import akka.http.scaladsl.{Http, HttpExt}
import akka.http.scaladsl.model._
import akka.util.ByteString
import com.cplier.mahina.rest._
import spray.json._

import scala.concurrent.duration._

object DataSyncWorker {
  final case class EntityEnvelope(id: String, data: Any)
  object EntityEnvelope {
    // the identity is use for calculate the percentages
    val Identity = "DataSyncWorkerIdentify"
  }
  final case class IdleBullet(key: String, instant: Instant, save: Boolean, delete: Boolean)
  final case class SyncInit(data: Set[IdleBullet])
  final case class SyncUpdate(key: String)
  final case class SyncOne(uri: String, key: String, instant: Instant, save: Boolean, delete: Boolean)
  final case class SyncOneDone(key: String)
  final case object SyncAllDone
  final case object Ruin

  final case class GetRidOfKey(key: String)
  final case class FetchKeyValue(uri: String, key: String)
  final case class DiffMetaBullet(lkey: String, rkey: String, rValue: String)

  final case object GetInstant
  final case class GetInstantResult(instant: Instant)
  final case object GetOrigin
  final case class GetOriginResult(origin: Map[String, Instant])
  final case object GetSyncKeys
  final case class GetSyncKeysResult(syncKeys: Set[String])
}

/**
  * A sharding sync worker
  */
class DataSyncWorker(ecache: ActorRef, mcache: ActorRef)
    extends Actor
    with ActorLogging
    with ApiResponseJsonProtocol {
  import DataSyncWorker._
  import DistributedReceptionist._

  import akka.cluster.sharding.ShardRegion.Passivate

  import akka.pattern.pipe
  import context.dispatcher

  implicit val system: ActorSystem = context.system
  val http: HttpExt                = Http(system)

  context.setReceiveTimeout(2 minute)

  var instant: Instant             = Instant.now()
  var origin: Map[String, Instant] = Map()
  var syncKeys: Set[String]        = Set()
  var replyTo: ActorRef            = _

  val syncRegion: ActorRef = ClusterSharding(context.system).shardRegion("Sync")

  override def receive: Receive = Stage_0

  def Stage_0: Receive = {
    case SyncInit(data) =>
      log.info("starting sync...")
      instant = Instant.now()
      origin = data.map(k => k.key -> k.instant).toMap
      syncKeys = Set()
    case SyncUpdate(key) =>
      syncKeys += key
    case GetInstant =>
      sender() ! GetInstantResult(instant)
    case GetOrigin =>
      sender() ! GetOriginResult(origin)
    case GetSyncKeys =>
      sender() ! GetSyncKeysResult(syncKeys)
    case SyncOne(uri, key, _, _, delete) =>
      replyTo = sender()
      if (delete) {
        // if mark as delete, try to delete local data
        context become Stage_1_Remove_Local_Key
        self ! GetRidOfKey(key)
      } else {
        context become Stage_1_Fetch_Remote_Key
        self ! FetchKeyValue(uri, key)
      }
    case SyncOneDone(key: String) =>
      log.info("Sync Key: " + key)
      syncRegion ! EntityEnvelope(EntityEnvelope.Identity, SyncUpdate(key))
    case ReceiveTimeout =>
      log.info("too long for waiting sync")
      context.parent ! Passivate(stopMessage = Ruin)
    case Ruin => context.stop(self)
  }

  def Stage_1_Remove_Local_Key: Receive = {
    case GetRidOfKey(key) =>
      val receptionist = system.actorOf(Props(new DistributedReceptionist(ecache, mcache)))
      receptionist ! DeleteIn(key)
    case FullResult(key: String) =>
      context become Stage_0
      replyTo ! SyncOneDone(key)
    case Failure(FailureType.Service, ErrorMessage("500", Some(msg), _), _) =>
      log.error(msg)
      context become Stage_0
      context.parent ! Passivate(stopMessage = Ruin)
    case Ruin => context.stop(self)
    case _ =>
      context become Stage_0
      self ! PoisonPill
  }

  def Stage_1_Fetch_Remote_Key: Receive = {
    case FetchKeyValue(uri, key) =>
      context become Stage_2_Handle_Remote_Resp(key)
      http.singleRequest(HttpRequest(uri = Uri(uri).withPath(Path / "replica" / key))) pipeTo self
    case _ =>
      context become Stage_0
      self ! PoisonPill
  }

  def Stage_2_Handle_Remote_Resp(localKey: String): Receive = {
    case HttpResponse(StatusCodes.OK, _, entity, _) =>
      val futResp = entity.dataBytes.runFold(ByteString(""))(_ ++ _).map(_.utf8String)
      context become Stage_3_Handle_Local_Update(localKey)
      futResp.map(l => l.parseJson.convertTo[ApiResponse[ResponseData]]) pipeTo self
    case resp @ HttpResponse(code, _, _, _) =>
      log.error("Request failed, response code: " + code)
      resp.discardEntityBytes()
      context become Stage_0
      self ! PoisonPill
  }

  def Stage_3_Handle_Local_Update(localKey: String): Receive = {
    case ApiResponse(_, Some(ResponseData(key, value))) =>
      system.actorOf(Props(new DistributedReceptionist(ecache, mcache))) ! PutIn(key, value)
    case ApiResponse(_, None) =>
      // if not found, delete local key
      system.actorOf(Props(new DistributedReceptionist(ecache, mcache))) ! DeleteIn(localKey)
    case FullResult(key: String) =>
      context become Stage_0
      self ! SyncOneDone(key)
    case Failure(FailureType.Service, ErrorMessage("500", Some(msg), _), _) =>
      log.error(msg)
      context become Stage_0
      context.parent ! Passivate(stopMessage = Ruin)
    case Ruin => context.stop(self)
    case _ =>
      context become Stage_0
      self ! PoisonPill
  }
}
