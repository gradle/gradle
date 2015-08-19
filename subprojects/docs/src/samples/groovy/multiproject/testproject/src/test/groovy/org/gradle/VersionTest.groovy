package org.gradle

import org.junit.Test

import static org.junit.Assert.assertEquals

class GroovycVersionTest {
  def groovycVersion

  @Test
  void versionShouldBe2_4_4() {
    assertEquals("2.4.4", groovycVersion)
  }
}
