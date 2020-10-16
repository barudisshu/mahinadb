package com.cplier.mahina.audit

import java.time.Instant

import akka.actor._
import akka.cluster.ddata.Replicator._
import com.cplier.mahina.dcache._

import scala.concurrent.duration._

object DistributedAudit {
  final case class Note(key: String, action: Action)
  final case object GetRecordMsg
  final case class RecordMsg(msg: Map[String, Instant])
  final case class NoteUpdated(key: String)
  val AllKeyInstantIdentify = "AllKeyInstantIdentify"

  final case object FetchKeySeq
  final case object UpdateKeySeq
  final case object FetchAllKeySeq
}

/**
  * The actor audit
  */
class DistributedAudit(mcache: ActorRef) extends Actor with ActorLogging {
  import DistributedAudit._
  import DistributedCacheReplicator._

  // initial the key sequence in the cluster, the key/timestamp pair
  var keySeq: Map[String, Instant] = Map()

  var currentKey: String    = _
  var currentAction: Action = NONE
  var replyTo: ActorRef     = _

  override def receive: Receive = init

  def init: Receive = {
    case Note(key, action) =>
      currentKey = key
      currentAction = action
      replyTo = sender()
      context become fetchKeySeq
      self ! FetchKeySeq
    case GetRecordMsg =>
      replyTo = sender()
      context become fetchAllKeySeq
      self ! FetchAllKeySeq
  }

  // get key seq from cluster memory
  def fetchKeySeq: Receive = {
    case FetchKeySeq =>
      context become handleKeySeq
      mcache ! GetFromCache(AllKeyInstantIdentify, ReadMajorityPlus(5.seconds, 1))
  }

  def handleKeySeq: Receive = {
    case CacheElement(_, Some(KeySeqDdata(tdata))) =>
      keySeq = tdata.map(k => k.key -> k.instant).toMap
      currentAction match {
        case PUT =>
          keySeq += currentKey -> Instant.now()
        case DEL =>
          keySeq -= currentKey
        case GET =>
        case _   =>
      }
      context become updateKeySeq()
      self ! UpdateKeySeq
    case CacheElement(_, None) =>
      keySeq += currentKey -> Instant.now()
      context become updateKeySeq()
      self ! UpdateKeySeq
  }

  def updateKeySeq(): Receive = {
    case UpdateKeySeq =>
      context become handleKeySeqUpdate
      mcache ! PutToCache(AllKeyInstantIdentify,
                          KeySeqDdata(keySeq.map(k => keyInstantDdata(k._1, k._2)).toSet),
                          WriteMajorityPlus(5.seconds, 1))
  }

  def handleKeySeqUpdate: Receive = {
    case CacheUpdated(key, _) =>
      keySeq = Map()
      replyTo ! NoteUpdated(currentKey)
      self ! PoisonPill
  }

  def fetchAllKeySeq: Receive = {
    case FetchAllKeySeq =>
      context become handleAllKeySeq
      mcache ! GetFromCache(AllKeyInstantIdentify, ReadMajorityPlus(5.seconds, 1))
  }

  def handleAllKeySeq: Receive = {
    case CacheElement(_, Some(KeySeqDdata(ks))) =>
      keySeq = ks.map(k => k.key -> k.instant).toMap
      replyTo ! RecordMsg(keySeq)
    case CacheElement(_, None) =>
      replyTo ! RecordMsg(keySeq)

  }
}
