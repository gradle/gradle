package org.gradle.sample

import org.apache.commons.collections.list.GrowthList

/**
 * Defines the traits of one who is named.
 */
trait Named:
  def names: List[String]

  def importedList: GrowthList
