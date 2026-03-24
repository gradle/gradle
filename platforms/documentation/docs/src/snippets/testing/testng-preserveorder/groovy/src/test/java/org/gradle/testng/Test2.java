package org.gradle.testng;

import java.io.Serializable;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class Test2 {

    public static class C implements Serializable {
        private static final long serialVersionUID = 1L;
    }

    @BeforeClass
    public void beforeClass() {
        System.out.println("Test2.beforeClass()");
    }

    @Test
    public void test1() {
        System.out.println("Test2.test1()");
    }

    @Test(dependsOnMethods = {"test1"})
    public void test2() {
        System.out.println("Test2.test2()");
    }

    @AfterClass
    public void afterClass() {
        System.out.println("Test2.afterClass()");
    }
}
