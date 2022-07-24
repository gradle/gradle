package org.gradle;

import org.junit.runner.RunWith;
import org.junit.Test;


@RunWith(CustomRunner.class)
public class ExecutionOrderTest {

    static{
        CustomRunner.isClassUnderTestLoaded = true;
    }

	@Test
	public void classUnderTestIsLoadedOnlyByRunner(){
		// The CustomRunner class will fail this test if this class is initialized before its 
		// run method is triggered. 
	}
	
}
