package org.gradle.sample

import org.apache.commons.collections.list.GrowthList

class Person(val names: List[String]) extends Named:
  override def importedList = new GrowthList
