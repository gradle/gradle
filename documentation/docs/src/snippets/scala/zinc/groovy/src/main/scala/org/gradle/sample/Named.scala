package org.gradle.sample

import org.apache.commons.collections.list.GrowthList

trait Named {
  def names: List[String]

  def importedList: GrowthList
}
