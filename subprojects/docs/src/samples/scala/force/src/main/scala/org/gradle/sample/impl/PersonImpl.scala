package org.gradle.sample.impl

import org.gradle.sample.api.Person
import org.apache.commons.collections.list.GrowthList;

/**
 * Immutable implementation of {@link Person}.
 */
class PersonImpl(val names: List[String]) extends Person
{
  private val importedList = new GrowthList();
}
