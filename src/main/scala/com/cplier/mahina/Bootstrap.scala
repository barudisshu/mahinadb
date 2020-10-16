package com.cplier.mahina

import akka.actor._
import akka.cluster.sharding._
import com.cplier.mahina.dc._
import com.cplier.mahina.dcache._

import scala.concurrent.ExecutionContext

trait Bootstrap {
  this: Configuration =>

  implicit val as: ActorSystem      = system
  implicit val ec: ExecutionContext = system.dispatcher

  val ecache: ActorRef = system.actorOf(Props(new DistributedCacheReplicator("replica")))
  val mcache: ActorRef = system.actorOf(Props(new DistributedCacheReplicator("monitor")))

  val sync: ActorRef = ClusterSharding(system).start(
    typeName = "Sync",
    entityProps = Props(classOf[DataSyncWorker], ecache, mcache),
    settings = ClusterShardingSettings(system),
    extractEntityId = extractEntityId,
    extractShardId = extractShardId
  )

  val syncRegion: ActorRef = ClusterSharding(system).shardRegion("Sync")
}
