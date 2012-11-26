/*
 * Copyright 2012 the original author or authors.
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




package org.gradle.testing

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Unroll

public class JUnitXmlResultsIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        executer.allowExtraLogging = false
    }

    @Unroll
    def "produces encoded xml when #testSettings"() {
        buildFile << """
            apply plugin: 'java'
                repositories { mavenCentral() }
                dependencies { testCompile 'org.testng:testng:6.3.1', 'junit:junit:4.10' }

            test {
                testReport = true
                $testSettings
            }
        """

        file("src/test/java/FooTest.java") << """
$imports

public class FooTest {
    @Test public void encodesCdata() {
        System.out.println("< html allowed, cdata closing token ]]> encoded!");
        System.err.println("< html allowed, cdata closing token ]]> encoded!");
    }
    @Test public void encodesAttributeValues() {
        throw new RuntimeException("html: <> cdata: ]]>");
    }
}
"""
        when:
        runAndFail("test")

        then:
        def result = file("build/test-results/TEST-FooTest.xml").text
        //cdata is correctly encoded:
        result.count("< html allowed, cdata closing token ]]&gt; encoded!") == 2
        //attribute values are encoded:
        result.contains('message="java.lang.RuntimeException: html: &lt;&gt; cdata: ]]&gt;"')
        //text is encoded:
        result.contains('>java.lang.RuntimeException: html: &lt;&gt; cdata: ]]&gt;')

        where:
        testSettings  | imports
        "useTestNG()" | "import org.testng.annotations.*;"
        //TODO SF enable JUnit for consistency once we figure what we do with CDATA encoding
//        "useJUnit()"  | "import org.junit.*;"

    }
}
