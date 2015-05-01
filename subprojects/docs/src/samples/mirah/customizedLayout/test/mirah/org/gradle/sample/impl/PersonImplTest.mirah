package org.gradle.sample.impl

import java.util.List
import junit.framework.TestCase
import org.gradle.sample.api.Person

class PersonImplTest < TestCase

  # FIXME: use a Mirah test framework to run a test
  def testCanCreatePersonImpl():void
    PersonImpl.new(["bob", "smith"])
  end

end
