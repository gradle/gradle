package org.gradle.sample

class Person(val name: String) extends PersonLike {
  override def getName: String = name
}
