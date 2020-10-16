package com.cplier.mahina.service

import akka.actor._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import com.cplier.mahina.dc._
import com.cplier.mahina.rest.BasicRoutesDefinition

import scala.concurrent.ExecutionContext

/**
  * Sync data with timestamp
  */
class SyncRoute(mcache: ActorRef, syncRegion: ActorRef)(implicit ec: ExecutionContext) extends BasicRoutesDefinition {
  import DataCrawler._
  import DataSyncMonitor._

  override def routes(implicit system: ActorSystem, ec: ExecutionContext, mater: Materializer): Route = {
    logRequestResult("sync") {
      pathPrefix("sync") {
        path("full") {
          post {
            entity(as[FullDose]) { fullDose =>
              serviceAndComplete[String](fullDose, crawlerInstance(system))
            }
          }
        } ~ path("delta") {
          post {
            entity(as[DeltaDose]) { deltaDose =>
              serviceAndComplete[String](deltaDose, crawlerInstance(system))
            }
          }
        } ~ path("last_sync_percentage") {
          get {
            serviceAndComplete[SyncResult](Inspect, monitorInstance(system))
          }
        }
      }
    }
  }

  private def crawlerInstance(system: ActorSystem): ActorRef = {
    system.actorOf(Props(new DataCrawler(mcache, syncRegion)))
  }

  private def monitorInstance(system: ActorSystem): ActorRef = {
    system.actorOf(Props(new DataSyncMonitor(syncRegion)))
  }
}
