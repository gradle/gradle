package org.gradle;
public class BadTest {
   /** 
    * @testng.test 
    */
   public void passingTest() { throw new IllegalArgumentException(); }
}
