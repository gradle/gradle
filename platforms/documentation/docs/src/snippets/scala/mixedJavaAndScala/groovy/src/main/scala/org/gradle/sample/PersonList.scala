package org.gradle.sample

object PersonList {
  def find(name: String): Named = new JavaPerson(name)
}
