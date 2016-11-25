package org.gradle.sample.impl

import org.gradle.sample.Person

class PersonImpl(val name: String) extends Person {
  override def getName: String = name
}
