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
package org.gradle.testing

import org.gradle.integtests.fixtures.*
import org.gradle.integtests.fixtures.internal.*

import spock.lang.*

@Issue("http://issues.gradle.org/browse/GRADLE-1948")
class InterruptedTestThreadIntegrationTest extends AbstractIntegrationSpec {

    @Timeout(30)
    def "test interrupting its own thread does not kill test execution"() {
        given:
        buildFile << """
            apply plugin: 'java'
            repositories { mavenCentral() }
            dependencies { testCompile "junit:junit:4.8.2" }
        """
        
        and:
        file("src/test/java/SomeTest.java") << """
            import org.junit.*;

            public class SomeTest {
                @Test public void foo() {
                    Thread.currentThread().interrupt();
                }
            }
        """
        
        when:
        run "test"
        
        then:
        ":test" in nonSkippedTasks
    }  
 
}