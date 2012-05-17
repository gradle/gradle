/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.java

import org.gradle.integtests.fixtures.WellBehavedPluginTest
import spock.lang.Issue
import spock.lang.Unroll

class JavaPluginGoodBehaviourTest extends WellBehavedPluginTest {
    @Override
    String getMainTask() {
        return "build"
    }

    @Issue("http://issues.gradle.org/browse/GRADLE-2313")
    @Unroll
    "can clean test after extracting class file with #framework"() {
        when:
        buildFile << """
            apply plugin: "java"
            repositories.mavenCentral()
            dependencies { testCompile "$dependency" }
            test { $framework() }
        """
        and:
        file("src/test/java/SomeTest.java") << """
            public class SomeTest extends $superClass {
            }
        """
        then:
        succeeds "clean", "test"

        and:
        file("build/tmp/test").exists() // ensure we extracted classes

        where:
        framework   | dependency                | superClass
        "useJUnit"  | "junit:junit:4.10"        | "org.junit.runner.Result"
        "useTestNG" | "org.testng:testng:6.3.1" | "org.testng.Converter"


    }

}