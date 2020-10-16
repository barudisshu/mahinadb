package com.cplier.mahina.service

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import com.cplier.mahina.audit._
import com.cplier.mahina.rest.BasicRoutesDefinition

import scala.concurrent.ExecutionContext

class ReplicaRoute(ecache: ActorRef, mcache: ActorRef)(implicit ec: ExecutionContext) extends BasicRoutesDefinition {
  import DistributedReceptionist._
  import AuditReceptionist._

  override def routes(implicit system: ActorSystem, ec: ExecutionContext, mater: Materializer): Route = {
    logRequestResult("replica") {
      pathPrefix("replica") {
        get {
          concat(
            path(Segment) { key =>
              serviceAndComplete[ResponseData](GetIn(key), receptionistInstance(system))
            },
            pathEnd {
              serviceAndComplete[KeysResponse](GetKeys, regionInstance(system))
            }
          )
        } ~
          put {
            entity(as[PutIn]) { putIn =>
              serviceAndComplete[String](putIn, receptionistInstance(system))
            }
          } ~
          delete {
            path(Segment) { key =>
              serviceAndComplete[String](DeleteIn(key), receptionistInstance(system))
            }
          }
      }
    }
  }

  private def receptionistInstance(system: ActorSystem): ActorRef = {
    system.actorOf(Props(new DistributedReceptionist(ecache, mcache)))
  }

  private def regionInstance(system: ActorSystem): ActorRef = {
    system.actorOf(Props(new AuditReceptionist(mcache)))
  }
}
