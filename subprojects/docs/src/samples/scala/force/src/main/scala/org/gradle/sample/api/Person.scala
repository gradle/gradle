package org.gradle.sample.api

import org.apache.commons.collections.list.GrowthList

trait Person {
  def names: List[String]

  def importedList: GrowthList
}
