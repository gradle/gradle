/*
 * Copyright 2010 the original author or authors.
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


package org.gradle.integtests.testng

/**
 * @author Tom Eyckmans
 */

public class TestNGIntegrationProject {
    String name
    boolean expectFailure
    Closure assertClosure

    static TestNGIntegrationProject failingIntegrationProject(String language, String jdk, assertClosure)
    {
        new TestNGIntegrationProject(language + "-" + jdk + "-failing", true, null, assertClosure)
    }

    static TestNGIntegrationProject failingIntegrationProject(String language, String jdk, String nameSuffix, assertClosure)
    {
        new TestNGIntegrationProject(language + "-" + jdk + "-failing", true, nameSuffix, assertClosure)
    }

    static TestNGIntegrationProject passingIntegrationProject(String language, String jdk, assertClosure)
    {
        new TestNGIntegrationProject(language + "-" + jdk + "-passing", false, null, assertClosure)
    }

    static TestNGIntegrationProject passingIntegrationProject(String language, String jdk, String nameSuffix, assertClosure)
    {
        new TestNGIntegrationProject(language + "-" + jdk + "-passing", false, nameSuffix, assertClosure)
    }

    public TestNGIntegrationProject(String name, boolean expectFailure, String nameSuffix, assertClosure)
    {
        if ( nameSuffix == null ) {
            this.name = name
        } else {
            this.name = name + nameSuffix
        }
        this.expectFailure = expectFailure
        this.assertClosure = assertClosure
    }

    void doAssert(projectDir, result) {
        if (assertClosure.maximumNumberOfParameters == 3) {
            assertClosure(name, projectDir, new TestNGExecutionResult(projectDir))
        } else {
            assertClosure(name, projectDir, new TestNGExecutionResult(projectDir), result)
        }
    }
}