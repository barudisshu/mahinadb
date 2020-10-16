package com.cplier.mahina.audit

import java.time.Instant

import akka.actor._
import com.cplier.mahina.rest.ServiceProtocol._
import com.cplier.mahina.rest._
import spray.json.RootJsonFormat

object AuditReceptionist {

  final case object GetKeys
  final case class KeysResponse(records: Map[String, Instant])
  object KeysResponse {
    implicit val toJson: RootJsonFormat[KeysResponse] = jsonFormat1(KeysResponse.apply)

  }
}

class AuditReceptionist(mcache: ActorRef) extends Actor with ActorLogging {
  import AuditReceptionist._
  import DistributedAudit._
  var replyTo: ActorRef = _

  override def receive: Receive = {
    case GetKeys =>
      replyTo = sender()
      context.actorOf(Props(new DistributedAudit(mcache))) ! GetRecordMsg
    case RecordMsg(records) =>
      replyTo ! FullResult(KeysResponse(records))
      self ! PoisonPill
  }
}
