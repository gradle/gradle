package org.gradle;
public class BadTest {
   @org.testng.annotations.Test
   public void failingTest() { throw new IllegalArgumentException('broken'); }
}
