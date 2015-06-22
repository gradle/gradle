package org.gradle.sample

import junit.framework.TestCase
import org.gradle.sample.impl.PersonImpl
import org.gradle.sample.impl.MirahPerson

class PersonTest < TestCase

  # FIXME: use a Mirah test framework to run a test
  def testCanCreateMirahPersonImpl():void
    PersonImpl.new("bob smith")
  end

  def testCanCreateMirahPerson():void
    MirahPerson.new("bob smith")
  end

end
