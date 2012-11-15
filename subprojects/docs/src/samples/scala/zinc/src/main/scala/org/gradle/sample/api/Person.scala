package org.gradle.sample.api

/**
 * Defines the interface for a person.
 */
abstract trait Person
{
  def names: List[String]
}
