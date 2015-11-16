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
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.testing.fixture.AbstractTestFilteringIntegrationTest
import org.gradle.testing.fixture.JUnitCoverage
import spock.lang.Ignore

@TargetCoverage({ JUnitCoverage.LARGE_COVERAGE })
public class JUnitFilteringIntegrationTest extends AbstractTestFilteringIntegrationTest {

    void configureFramework() {
        framework = "JUnit"
        dependency = "junit:junit"
        imports = "org.junit.*"
    }

    @Ignore
    def "can filter parameterized junit tests"() {
        buildFile << """
            test {
              filter {
                includeTestsMatching "ParameterizedFoo.pass"
              }
            }
        """
        file("src/test/java/ParameterizedFoo.java") << """import $imports;
            import org.junit.runners.Parameterized;
            import org.junit.runners.Parameterized.Parameters;
            import org.junit.runner.RunWith;
            import java.util.Arrays;
            import java.util.Collection;

            @RunWith(Parameterized.class)
            public class ParameterizedFoo {
                int index;
                public ParameterizedFoo(int index){
                    this.index = index;
                }

                @Parameters
                public static Collection data() {
                   return Arrays.asList(new Object[][] {
                      { 2 },
                      { 6 },
                      { 19 },
                      { 22 },
                      { 23 }
                   });
                }
                @Test public void pass() {}
                @Test public void fail() {}
            }
        """
        when:
        run("test")

        then:
        def result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted("ParameterizedFoo")
        result.testClass("ParameterizedFoo").assertTestsExecuted("pass")
    }
}
