package org.gradle.sample

import _root_.junit.framework.TestCase
import _root_.org.gradle.sample.impl.PersonImpl
import _root_.org.gradle.sample.impl.JavaPerson

class PersonTest extends TestCase {

  def testCanCreateScalaPersonImpl(): Unit = {
    def person: Person = new PersonImpl("bob smith")
    person
  }

  def testCanCreateJavaPersonImpl(): Unit = {
    def person: Person = new JavaPerson("bob smith")
    person
  }

}
