package org.gradle

import org.junit.Test
import static org.junit.Assert.*

class GroovycVersionTest {
  def groovycVersion

  @Test
  void versionShouldBe1_7_10() {
    assertEquals("1.7.10", groovycVersion)
  }
}