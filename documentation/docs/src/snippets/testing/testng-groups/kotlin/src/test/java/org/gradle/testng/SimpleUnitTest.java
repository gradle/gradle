package org.gradle.testng;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.testng.annotations.AfterMethod;
import static org.testng.Assert.*;

public class SimpleUnitTest{
    @Test(groups = { "unitTests" })
    public void simpleUnitTest(){
        assertEquals(true, true);
    }
}
