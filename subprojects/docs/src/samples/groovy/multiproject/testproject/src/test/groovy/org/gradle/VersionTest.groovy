package org.gradle

import org.junit.Test
import static org.junit.Assert.*

class GroovycVersionTest {
  def groovycVersion

  @Test
  void versionShouldBe2_3_2() {
    assertEquals("2.3.2", groovycVersion)
  }
}