package org.gradle.sample.impl

import java.util.List
import org.gradle.sample.api.Person
import org.apache.commons.collections.list.GrowthList;

/**
 * Immutable implementation of {@link Person}.
 */
class PersonImpl implements Person
  attr_reader names:List
  
  def initialize(names:List)
    @names        = names
    @importedList = GrowthList.new();
  end
end
