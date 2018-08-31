/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
