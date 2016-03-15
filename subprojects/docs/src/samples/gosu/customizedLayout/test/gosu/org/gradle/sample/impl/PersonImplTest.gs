package org.gradle.sample.impl

uses org.gradle.sample.api.Person
uses org.junit.Assert
uses org.junit.Test

class PersonImplTest {

  @Test
  function testCanCreatePersonImpl() {
    var person = new PersonImpl( { "bob", "smith" } )
    Assert.assertNotNull(person)
    Assert.assertNotNull(person.Names)
  }
}
