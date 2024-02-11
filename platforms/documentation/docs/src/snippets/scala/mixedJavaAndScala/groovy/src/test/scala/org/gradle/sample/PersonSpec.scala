package org.gradle.sample

import org.junit.Test

class PersonSpec {
  @Test
  def createPersonShouldSucceedForScala(): Unit = {
    def person: Named = new Person("bob smith")
  }

  @Test
  def createPersonShouldSucceedForJava(): Unit = {
    def person: Named = new JavaPerson("alice smith")
  }
}
