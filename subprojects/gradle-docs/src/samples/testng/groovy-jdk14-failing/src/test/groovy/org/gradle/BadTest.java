package org.gradle;

import org.testng.Assert;

public class BadTest {
   /**
    * @testng.test
    */
   public void passingTest() { 
		Assert.fail();
	}
}
