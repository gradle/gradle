package org.gradle.sample

import org.junit.runner.RunWith
import org.scalatest.FunSpec
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class PersonSpec extends FunSpec {
  describe("person creation") {
    it("should successfully create a person") {
      val person: Named = new Person(List("bob", "smith"))
      person
    }
  }
}
