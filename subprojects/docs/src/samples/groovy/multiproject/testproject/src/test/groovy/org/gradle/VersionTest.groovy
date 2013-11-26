package org.gradle

import org.junit.Test
import static org.junit.Assert.*

class GroovycVersionTest {
  def groovycVersion

  @Test
  void versionShouldBe2_2_0() {
    assertEquals("2.2.0", groovycVersion)
  }
}