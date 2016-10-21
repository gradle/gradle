package org.gradle.sample.impl

import org.gradle.sample.api.Person
import org.junit.runner.RunWith
import org.scalatest.FunSpec
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class PersonImplSpec extends FunSpec {
  describe("person creation") {
    it("should successfully create a person") {
      val person: Person = new PersonImpl(List("bob", "smith"))
      person
    }
  }
}
