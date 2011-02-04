package org.gradle;
public class BadTest {
   /** 
    * @testng.test 
    */
   public void failingTest() { throw new IllegalArgumentException("broken"); }
}
