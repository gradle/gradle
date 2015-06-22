package org.gradle.sample.impl

import org.gradle.sample.Person

/**
 * Immutable implementation of {@link Person}.
 */
class PersonImpl implements Person
  attr_reader name:String
  
  def initialize(name:String)
  	@name = name
  end
  
  def getName
  	@name
  end
end

