/*
 * Copyright 2013 the original author or authors.
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


package org.gradle.testing.junit
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.testing.fixture.JUnitCoverage

@TargetCoverage({ JUnitCoverage.LARGE_COVERAGE })
public class JUnitEnclosedIntegrationTest extends MultiVersionIntegrationSpec {

    void setup() {
        buildFile << """
            apply plugin: 'java'
            repositories { mavenCentral() }
            dependencies { testCompile 'junit:junit:$version' }
            test { useJUnit() }
        """
    }

    def "runs enclosed junit tests only once"() {
        buildFile << """
            test {
              scanForTestClasses true // This will find and run tests inside inner classes, even without @RunWith(Enclosed.class).
            }
        """
        file('src/test/java/EnclosedTest.java') << """
            import org.junit.Test;
            import org.junit.experimental.runners.Enclosed;
            import org.junit.runner.RunWith;

            @RunWith(Enclosed.class)
            public class EnclosedTest {
                public static final class InnerTest {
                  @Test public void pass() {}
                }
            }
        """
        when:
        run('test')

        then:
        def result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('EnclosedTest$InnerTest')
        result.testClass('EnclosedTest$InnerTest').assertTestsExecuted('pass') // Expected to have run only once.
    }
}
