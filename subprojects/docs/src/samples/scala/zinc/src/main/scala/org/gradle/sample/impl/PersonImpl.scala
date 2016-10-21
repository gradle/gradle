package org.gradle.sample.impl

import org.gradle.sample.api.Person
import org.apache.commons.collections.list.GrowthList;

class PersonImpl(val names: List[String]) extends Person {
  override def importedList = new GrowthList
}
