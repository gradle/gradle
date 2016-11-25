package org.gradle.sample.impl

import org.gradle.sample.api.Person
import org.junit.runner.RunWith
import org.scalatest.FunSpec
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class PersonImplSpec extends FunSpec {
  describe("person creation") {
    it("should successfully create a person") {
      def person: Person = new PersonImpl(List("bob", "smith"))
      person
    }
  }
}
