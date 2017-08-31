package org.gradle

import org.junit.Test

import static org.junit.Assert.assertEquals

class GroovycVersionTest {
  def groovycVersion

  @Test
  void versionShouldBeCurrent() {
    assertEquals("2.4.10", groovycVersion)
  }
}
