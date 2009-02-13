package org.gradle;
public class BadTest {
   @org.testng.annotations.Test
   public void passingTest() { throw new IllegalArgumentException(); }
}
