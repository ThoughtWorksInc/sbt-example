package com.thoughtworks

private[thoughtworks] object NameEncoder {
  private val operatorNames = Map(
    '~' -> "$tilde",
    '=' -> "$eq",
    '<' -> "$less",
    '>' -> "$greater",
    '!' -> "$bang",
    '#' -> "$hash",
    '%' -> "$percent",
    '^' -> "$up",
    '&' -> "$amp",
    '|' -> "$bar",
    '*' -> "$times",
    '/' -> "$div",
    '+' -> "$plus",
    '-' -> "$minus",
    ':' -> "$colon",
    '\\' -> "$bslash",
    '?' -> "$qmark",
    '@' -> "$at"
  )

  def encode(name: String): String =
    name.iterator
      .map { char =>
        operatorNames.getOrElse(
          char,
          if (Character.isJavaIdentifierPart(char)) char.toString
          else f"$$u${char.toInt}%04X"
        )
      }
      .mkString
}
