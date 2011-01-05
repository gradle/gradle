package org.gradle.sample.impl

import _root_.junit.framework.TestCase
import _root_.org.gradle.sample.api.Person

class PersonImplTest extends TestCase {

  // FIXME: use a Scala test framework to run a test
  def testCanCreatePersonImpl(): Unit = {
    def person: Person = new PersonImpl(List("bob", "smith"))
    person
  }

}
