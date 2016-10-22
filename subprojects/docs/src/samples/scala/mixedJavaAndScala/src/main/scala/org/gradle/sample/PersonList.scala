package org.gradle.sample

object PersonList {
  def find(name: String): PersonLike = new JavaPerson(name)
}
