package com.thoughtworks

import scala.reflect.NameTransformer

private[thoughtworks] object NameEncoder {
  def encode(name: String): String = NameTransformer.encode(name)
}
