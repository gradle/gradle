package org.gradle

import org.junit.Test
import static org.junit.Assert.*

class GroovycVersionTest {
  def groovycVersion

  @Test
  void versionShouldBe1_8_8() {
    assertEquals("1.8.8", groovycVersion)
  }
}