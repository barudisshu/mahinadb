package com.cplier.mahina.dcache

import akka.actor.{ActorSystem, Props}
import akka.cluster.ddata.Replicator._
import akka.testkit._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class DistributedCacheReplicatorSpec
    extends TestKit(ActorSystem("TK"))
    with ImplicitSender
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll {

  import DistributedCacheReplicator._

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "distributed cache replicator" should {
    val dcache = system.actorOf(Props(new DistributedCacheReplicator("dcache")))
    "put an token" in {
      dcache ! PutToCache("key", StringDdata("value"), WriteLocal)
      expectMsg(CacheUpdated("key", PUT))
    }
    "get the key" in {
      dcache ! GetFromCache("key", ReadLocal)
      expectMsgClass(classOf[CacheElement])
    }
    "remove the key" in {
      dcache ! RemoveFromCache("key", WriteLocal)
      expectMsg(CacheUpdated("key", DEL))
    }
    "get the key again" in {
      dcache ! GetFromCache("key", ReadLocal)
      expectMsg(CacheElement("key", None))
    }
    "get not found key" in {
      dcache ! GetFromCache("key$", ReadLocal)
      expectMsg(CacheElement("key$", None))
    }
  }
}
