package com.cplier.mahina

import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.management.scaladsl.AkkaManagement
import com.cplier.mahina.service.{ReplicaRoute, SyncRoute}

import scala.io.StdIn

object Main extends App with Bootstrap with Configuration {

  if (clusterAkkaManagementEnabled.equalsIgnoreCase("enabled")) {
    system.log.info("Staring management service...")
    AkkaManagement(system).start()
  }

  val listener = system.actorOf(ClusterListener.props())

  val route = new ReplicaRoute(ecache, mcache).routes ~ new SyncRoute(mcache, syncRegion).routes

  val bindingFuture = Http().newServerAt(serviceIp, servicePort).bind(route)

  println(s"Server online at http://$serviceIp:$servicePort/\nPress RETURN to stop...")
  StdIn.readLine()
  bindingFuture.flatMap(_.unbind()).onComplete(_ => system.terminate())
}
