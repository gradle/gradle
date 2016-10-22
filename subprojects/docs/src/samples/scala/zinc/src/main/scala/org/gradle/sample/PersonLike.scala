package org.gradle.sample

import org.apache.commons.collections.list.GrowthList

trait PersonLike {
  def names: List[String]

  def importedList: GrowthList
}
