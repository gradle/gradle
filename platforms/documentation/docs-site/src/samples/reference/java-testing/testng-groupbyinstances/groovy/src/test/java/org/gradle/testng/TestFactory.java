package org.gradle.testng;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Factory;
import org.testng.annotations.Test;

public class TestFactory {

    @DataProvider(name = "data")
    public static Object[][] provide() {
        return new Object[][] {
            { "data1" },
            { "data2" }
        };
    }

    private String data;

    @Factory(dataProvider = "data")
    public TestFactory(String data) {
        this.data = data;
    }

    @BeforeClass
    public void beforeClass() {
        System.out.println("TestFactory[" + data + "].beforeClass()");
    }

    @Test
    public void test1() {
        System.out.println("TestFactory[" + data + "].test1()");
    }

    @Test(dependsOnMethods = {"test1"})
    public void test2() {
        System.out.println("TestFactory[" + data + "].test2()");
    }

    @AfterClass
    public void afterClass() {
        System.out.println("TestFactory[" + data + "].afterClass()");
    }
}
