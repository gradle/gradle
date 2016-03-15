package org.gradle.sample.impl

uses org.gradle.sample.api.Person

/**
 * Immutable implementation of {@link Person}.
 */
class PersonImpl implements Person {

  var _names : List<String> as readonly Names

  construct(names: List<String>) {
    _names = names
  }

}

