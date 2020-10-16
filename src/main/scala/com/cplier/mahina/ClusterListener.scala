package com.cplier.mahina

import akka.actor.{Actor, ActorLogging, Props}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._

class ClusterListener extends Actor with ActorLogging {
  val cluster: Cluster = Cluster(context.system)

  override def preStart(): Unit =
    cluster.subscribe(self, initialStateMode = InitialStateAsEvents, classOf[MemberEvent], classOf[UnreachableMember])

  override def postStop(): Unit = cluster.unsubscribe(self)

  def receive: Receive = {
    case MemberUp(member) =>
      log.info("Member is Up: {}", member.address)
    case UnreachableMember(member) =>
      log.info("Member detected as unreachable: {}", member)

    case MemberRemoved(member, previousStatus) =>
      log.info("Member is Removed: {} after {}", member.address, previousStatus)

    case LeaderChanged(member) =>
      log.info("Leader changed: " + member)

    case any: ClusterDomainEvent =>
      log.info("Cluster Domain Event: " + any.toString)

    case _ =>
      log.info("Cluster Domain Event: Unhandled")
  }
}

object ClusterListener {
  def props(): Props = Props(new ClusterListener())
}
