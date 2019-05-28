/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.testing.junitplatform

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.IgnoreIf
import static org.gradle.testing.fixture.JUnitCoverage.LATEST_JUPITER_VERSION

@Requires(TestPrecondition.JDK7_OR_EARLIER)
class JUnitPlatformOnJdk7IntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        buildFile << """ 
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies { 
                testImplementation 'org.junit.jupiter:junit-jupiter-api:${LATEST_JUPITER_VERSION}','org.junit.jupiter:junit-jupiter-engine:${LATEST_JUPITER_VERSION}'
            }
            
            test { useJUnitPlatform() }
            """
        file('src/test/java/org/gradle/JUnitJupiterTest.java') << '''
            package org.gradle;

            import org.junit.jupiter.api.Test;

            public class JUnitJupiterTest {
                @Test
                public void ok() { }
            }
            '''
    }

    def 'can forbid user to run JUnit platform on Java 7'() {
        when:
        def failure = fails('test')

        then:
        failure.assertHasCause('Running JUnit platform requires Java 8+')
    }

    @IgnoreIf({ AvailableJavaHomes.jdk8 == null })
    def 'can configure and run JUnit platform on Java 7'() {
        given:
        file('gradle.properties').writeProperties(["org.gradle.java.home": AvailableJavaHomes.jdk8.javaHome.absolutePath])

        when:
        succeeds('test')

        then:
        DefaultTestExecutionResult result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('org.gradle.JUnitJupiterTest')
        result.testClass('org.gradle.JUnitJupiterTest').assertTestPassed('ok')
    }
}
