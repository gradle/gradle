package org.gradle

import org.junit.Test
import static org.junit.Assert.*

class GroovycVersionTest {
  def groovycVersion

  @Test
  void versionShouldBe2_0_5() {
    assertEquals("2.0.5", groovycVersion)
  }
}