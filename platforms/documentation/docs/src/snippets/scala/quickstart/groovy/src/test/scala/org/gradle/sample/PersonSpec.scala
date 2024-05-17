package org.gradle.sample

import org.junit.Test

class PersonSpec {
  @Test
  def personCreationShouldSuccessfullyCreatePerson(): Unit = {
    def person: Named = new Person(List("bob", "smith"))
  }
}
