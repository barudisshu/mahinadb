package com.cplier.mahina.dc

import java.time.{Instant, ZoneId}
import java.time.format._

import akka.actor._
import com.cplier.mahina.rest.FullResult
import com.cplier.mahina.rest.ServiceProtocol._
import spray.json._

object DataSyncMonitor {

  case object Inspect
  case class SyncResult(instant: String,
                        totalKeyNumber: Int,
                        syncNumber: Int,
                        completeKeys: Set[String],
                        percentage: BigDecimal)

  object SyncResult {
    implicit val toJson: RootJsonFormat[SyncResult] = jsonFormat5(SyncResult.apply)
  }
}

class DataSyncMonitor(syncRegion: ActorRef) extends Actor with ActorLogging {

  import DataSyncMonitor._
  import DataSyncWorker._

  val formatter: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault())

  var replyTo: ActorRef               = _
  var tmpInstant: Instant             = Instant.now()
  var tmpOrigin: Map[String, Instant] = Map()
  var tmpSyncKeys: Set[String]        = Set()

  override def receive: Receive = Stage_0

  def Stage_0: Receive = {
    case Inspect =>
      replyTo = sender()
      context become Stage_1
      syncRegion ! EntityEnvelope(EntityEnvelope.Identity, GetOrigin)
  }

  def Stage_1: Receive = {
    case GetOriginResult(origin) =>
      tmpOrigin = origin
      context become Stage_2
      syncRegion ! EntityEnvelope(EntityEnvelope.Identity, GetSyncKeys)
  }

  def Stage_2: Receive = {
    case GetSyncKeysResult(syncKeys) =>
      tmpSyncKeys = syncKeys
      context become Stage_3
      syncRegion ! EntityEnvelope(EntityEnvelope.Identity, GetInstant)

  }

  def Stage_3: Receive = {
    case GetInstantResult(instant) =>
      tmpInstant = instant
      val syncResult = calculate(tmpInstant, tmpOrigin, tmpSyncKeys)
      replyTo ! FullResult(syncResult)
      self ! PoisonPill
  }

  private def calculate(instant: Instant, origin: Map[String, Instant], syncKeys: Set[String]): SyncResult = {
    val totalKeyNumber = origin.size
    val syncNumber     = syncKeys.size
    val completeKeys   = syncKeys
    val percentage     = if (syncNumber != 0 && totalKeyNumber != 0) syncNumber / totalKeyNumber else 0
    SyncResult(formatter.format(instant), totalKeyNumber, syncNumber, completeKeys, percentage)
  }
}
