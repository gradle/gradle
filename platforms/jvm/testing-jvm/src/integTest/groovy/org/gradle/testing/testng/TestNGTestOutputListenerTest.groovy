/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.testing.testng

class TestNGTestOutputListenerTest extends AbstractTestNGVersionIntegrationTest {
    def "shows standard stream also for testNG"() {
        given:
        def test = file("src/test/java/SomeTest.java")
        test << """
            import org.testng.*;
            import org.testng.annotations.*;

            public class SomeTest {
                @Test public void foo() {
                    System.out.println("output from foo");
                    System.err.println("error from foo");
                }
            }
        """.stripIndent()

        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies { testImplementation 'org.testng:testng:6.3.1' }

            test {
                useTestNG()
                testLogging.showStandardStreams = true
            }
        """.stripIndent()

        when: "run with quiet"
        executer.withArguments("-q")
        succeeds('test')

        then:
        outputDoesNotContain('output from foo')

        when: "run with lifecycle"
        executer.noExtraLogging()
        succeeds('cleanTest', 'test')

        then:
        outputContains('output from foo')
    }
}
