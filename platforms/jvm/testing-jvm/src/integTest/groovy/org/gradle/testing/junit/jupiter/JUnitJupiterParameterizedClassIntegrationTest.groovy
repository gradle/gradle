/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.testing.junit.jupiter

import org.gradle.api.tasks.testing.TestResult
import org.gradle.testing.junit.platform.JUnitPlatformIntegrationSpec

import static org.hamcrest.Matchers.equalTo

class JUnitJupiterParameterizedClassIntegrationTest extends JUnitPlatformIntegrationSpec {
    def "parameterized class has reasonable hierarchy"() {
        given:
        file('src/test/java/org/gradle/ParamClass.java') << '''
            package org.gradle;

            import org.junit.jupiter.api.Test;
            import org.junit.jupiter.params.ParameterizedClass;
            import org.junit.jupiter.params.provider.ValueSource;

            @ParameterizedClass
            @ValueSource(strings = { "one", "two" })
            public class ParamClass {
                private final String value;

                public ParamClass(String value) {
                    this.value = value;
                }

                @Test
                public void sayValue() {
                    System.out.println("Value: " + value);
                }
            }
            '''

        when:
        succeeds('test')

        then:
        def testResults = resultsFor(testDirectory)
        testResults.assertTestPathsExecuted(
            ":org.gradle.ParamClass:org.gradle.ParamClass[1]:sayValue()",
            ":org.gradle.ParamClass:org.gradle.ParamClass[2]:sayValue()"
        )
        testResults.testPathPreNormalized(":org.gradle.ParamClass:org.gradle.ParamClass[1]").onlyRoot()
            .assertDisplayName(equalTo("[1] one"))
        testResults.testPathPreNormalized(":org.gradle.ParamClass:org.gradle.ParamClass[2]").onlyRoot()
            .assertDisplayName(equalTo("[2] two"))
        testResults.testPath(":org.gradle.ParamClass:org.gradle.ParamClass[1]:sayValue()").onlyRoot()
            .assertHasResult(TestResult.ResultType.SUCCESS)
            .assertStdout(equalTo("Value: one\n"))
        testResults.testPath(":org.gradle.ParamClass:org.gradle.ParamClass[2]:sayValue()").onlyRoot()
            .assertHasResult(TestResult.ResultType.SUCCESS)
            .assertStdout(equalTo("Value: two\n"))
    }
}
