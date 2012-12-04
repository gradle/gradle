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
import org.gradle.integtests.fixtures.executer.GradleDistributionExecuter
import spock.lang.IgnoreIf
import spock.lang.Unroll

@IgnoreIf({ !GradleDistributionExecuter.systemPropertyExecuter.forks })
public class TestResultsMemoryIntegrationTest extends AbstractIntegrationSpec {

    @Unroll
    def "deals with huge test output when #testSettings"() {
        buildFile << """
            apply plugin: 'java'
                repositories { mavenCentral() }
                dependencies { testCompile 'org.testng:testng:6.3.1', 'junit:junit:4.10' }

            test {
                //TODO SF enable after fixing html report
                testReport = false
                $testSettings
            }
        """

        file("src/test/java/FooTest.java") << """
$imports

public class FooTest {
    @Test public void highOutput() {
        StringBuilder huge = new StringBuilder();
        for (int i=0; i<18; i++) {
            huge.append("xxxxx" + huge.toString());
            System.out.println(huge.toString());
            System.err.println(huge.toString());
        }
    }
}
"""
        when:
        def result = executer.withTasks('test').withGradleOpts("-Xmx40m").run()

        then:
        result

        where:
        testSettings  | imports
        "useTestNG()" | "import org.testng.annotations.*;"
        "useJUnit()"  | "import org.junit.*;"
    }
}
