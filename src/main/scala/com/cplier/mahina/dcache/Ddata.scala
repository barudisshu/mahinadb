package com.cplier.mahina.dcache

import java.time.Instant

trait Action
case object NONE extends Action
case object GET extends Action
case object PUT extends Action
case object DEL extends Action


sealed trait Ddata extends java.io.Serializable
final case class StringDdata(string: String) extends Ddata
final case class IntDdata(numeric: Int)      extends Ddata

final case class keyInstantDdata(key: String, instant: Instant) extends Ddata
final case class KeySeqDdata(ks: Set[keyInstantDdata]) extends Ddata
