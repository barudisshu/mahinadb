package com.cplier.mahina.service

import akka.actor._
import akka.cluster.ddata.Replicator._
import com.cplier.mahina.audit._
import com.cplier.mahina.dcache.DistributedCacheReplicator._
import com.cplier.mahina.dcache._
import com.cplier.mahina.rest.ServiceProtocol._
import com.cplier.mahina.rest._
import spray.json.RootJsonFormat

import scala.concurrent.duration._

object DistributedReceptionist {
  case class GetIn(key: String)
  object GetIn {
    implicit val toJson: RootJsonFormat[GetIn] = jsonFormat1(GetIn.apply)
  }
  case class PutIn(key: String, value: StringDdata)
  object PutIn {
    implicit val toJson: RootJsonFormat[PutIn] = jsonFormat2(PutIn.apply)
  }
  case class DeleteIn(key: String)
  object DeleteIn {
    implicit val toJson: RootJsonFormat[DeleteIn] = jsonFormat1(DeleteIn.apply)
  }
  case class ResponseData(key: String, value: StringDdata)
  object ResponseData {
    implicit val toJson: RootJsonFormat[ResponseData] = jsonFormat2(ResponseData.apply)
  }
}

class DistributedReceptionist(ecache: ActorRef, mcache: ActorRef) extends Actor with ActorLogging {
  import DistributedAudit._
  import DistributedReceptionist._
  var replyTo: ActorRef = _
  override def receive: Receive = {
    case GetIn(key) =>
      replyTo = sender()
      ecache ! GetFromCache(key, ReadMajorityPlus(5.seconds, 1))
    case PutIn(key, value) =>
      replyTo = sender()
      ecache ! PutToCache(key, value, WriteMajorityPlus(5.seconds, 1))
    case DeleteIn(key) =>
      replyTo = sender()
      ecache ! RemoveFromCache(key, WriteMajorityPlus(5.seconds, 1))
    case CacheElement(key, Some(StringDdata(value))) =>
      replyTo ! FullResult(ResponseData(key, StringDdata(value)))
      self ! PoisonPill
    case CacheElement(_, None) =>
      replyTo ! EmptyResult
      self ! PoisonPill
    case CacheOpException(msg, exception) =>
      replyTo ! Failure(FailureType.Service, ErrorMessage("500", Some(msg)), exception)
      self ! PoisonPill
    case CacheUpdated(key, action) =>
      context.actorOf(Props(new DistributedAudit(mcache))) ! Note(key, action)
    case NoteUpdated(key) =>
      replyTo ! FullResult(key)
      self ! PoisonPill
  }
}
