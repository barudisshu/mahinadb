package com.cplier.mahina.dcache

import akka.actor._
import akka.cluster._
import akka.cluster.ddata.Replicator._
import akka.cluster.ddata._

import scala.concurrent.ExecutionContext

object DistributedCacheReplicator {
  case class CacheContext(key: String, action: Action, replyTo: ActorRef)
  case class CacheElement(key: String, value: Option[Ddata])
  case class CacheUpdated(key: String, action: Action)
  case class CacheOpException(msg: String, cause: Option[Throwable] = None) extends RuntimeException(msg, cause.orNull)
  case class PutToCache(key: String, value: Ddata, writeConsistency: WriteConsistency)
  case class GetFromCache(key: String, readConsistency: ReadConsistency)
  case class RemoveFromCache(key: String, writeConsistency: WriteConsistency)

}

class DistributedCacheReplicator(name: String) extends Actor with ActorLogging {
  import DistributedCacheReplicator._

  val distributedData: DistributedData = DistributedData(context.system)
  val replicator: ActorRef             = distributedData.replicator

  implicit val selfUniqueAddress: SelfUniqueAddress = distributedData.selfUniqueAddress
  implicit val cluster: Cluster                     = Cluster(context.system)
  implicit val ec: ExecutionContext                 = context.system.dispatcher

  override def receive: Receive = {
    case PutToCache(key, value: Ddata, writeConsistency) =>
      val bk  = bucketKey(key)
      val ctx = Some(CacheContext(key, PUT, sender()))
      replicator ! Update(bk, LWWMap(), writeConsistency, ctx)(_ :+ (key -> value))
    case GetFromCache(key, readConsistency) =>
      val bk  = bucketKey(key)
      val ctx = Some(CacheContext(key, GET, sender()))
      replicator ! Get(bk, readConsistency, ctx)
    case RemoveFromCache(key, writeConsistency) =>
      val bk  = bucketKey(key)
      val ctx = Some(CacheContext(key, DEL, sender()))
      replicator ! Update(bk, LWWMap(), writeConsistency, ctx)(_ remove (selfUniqueAddress, key))
    case g @ GetSuccess(_, Some(CacheContext(key, _, replyTo))) =>
      g.dataValue match {
        case data: LWWMap[_, _] =>
          data.asInstanceOf[LWWMap[String, Ddata]].get(key) match {
            case Some(value) =>
              replyTo ! CacheElement(key, Some(value))
            case None =>
              replyTo ! CacheElement(key, None)
          }
      }
    case GetFailure(_, Some(CacheContext(_, GET, replyTo))) =>
      replyTo ! CacheOpException("Get operation failed due to ReadConsistency")
    case NotFound(_, Some(CacheContext(key, GET, replyTo))) =>
      replyTo ! CacheElement(key, None)
    case UpdateSuccess(_, Some(CacheContext(key, action, replyTo))) =>
      replyTo ! CacheUpdated(key, action)
    case UpdateTimeout(_, Some(CacheContext(_, _, replyTo))) =>
      replyTo ! CacheOpException("Update operation timed out")
    case ModifyFailure(_, msg, cause, Some(CacheContext(_, _, replyTo))) =>
      replyTo ! CacheOpException(s"Update operation failed due to $msg", Some(cause))
    case StoreFailure(_, Some(CacheContext(_, _, replyTo))) =>
      replyTo ! CacheOpException("Durable stores error")
  }

  def bucketKey(entryKey: String): LWWMapKey[String, Ddata] = {
    LWWMapKey(s"cache-$name:[$entryKey]")
  }
}
