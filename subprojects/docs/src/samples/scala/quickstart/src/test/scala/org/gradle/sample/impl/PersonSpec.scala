package org.gradle.sample.impl

import org.gradle.sample.api.Person
import org.junit.runner.RunWith
import org.scalatest.FunSpec
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class PersonSpec extends FunSpec {
  describe("create a person") {
    it("should succeed for scala") {
      val person: Person = new PersonImpl(List("bob smith"))
      person
    }
  }
}
