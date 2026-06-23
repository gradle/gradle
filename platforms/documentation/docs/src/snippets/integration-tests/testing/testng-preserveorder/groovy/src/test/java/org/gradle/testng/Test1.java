package org.gradle.testng;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class Test1 {

    @BeforeClass
    public void beforeClass() {
        System.out.println("Test1.beforeClass()");
    }

    @Test
    public void test1() {
        System.out.println("Test1.test1()");
    }

    @Test(dependsOnMethods = {"test1"})
    public void test2() {
        System.out.println("Test1.test2()");
    }

    @AfterClass
    public void afterClass() {
        System.out.println("Test1.afterClass()");
    }
}
