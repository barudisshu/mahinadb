package com.cplier.mahina

import akka.actor.ActorSystem
import akka.cluster.sharding.ShardRegion
import com.cplier.mahina.dc._
import com.typesafe.config._

trait Configuration {

  // clustering
  lazy val sysConfig: Config                    = ConfigFactory.load()
  lazy val clusterName: String                  = sysConfig.getString("clustering.cluster.name")
  lazy val clusterAkkaManagementEnabled: String = sysConfig.getString("clustering.akka.management.enabled")
  lazy val system: ActorSystem                  = ActorSystem(clusterName, sysConfig)

  // service
  lazy val serviceIp: String = sysConfig.getString("clustering.service-ip")
  lazy val servicePort: Int  = sysConfig.getInt("clustering.service-port")

  import DataSyncWorker._

  // sharding
  lazy val extractEntityId: ShardRegion.ExtractEntityId = {
    case EntityEnvelope(id, payload) => (id, payload)
  }

  lazy val numberOfShards = 100

  lazy val extractShardId: ShardRegion.ExtractShardId = {
    case EntityEnvelope(id, _) => (math.abs(id.hashCode) % numberOfShards).toString
  }

}
