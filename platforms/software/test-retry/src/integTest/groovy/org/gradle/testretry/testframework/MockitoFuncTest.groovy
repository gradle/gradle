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
package org.gradle.testretry.testframework

import org.gradle.testretry.AbstractFrameworkFuncTest

class MockitoFuncTest extends AbstractFrameworkFuncTest {

    @Override
    protected String buildConfiguration() {
        super.buildConfiguration() + """
            dependencies {
                testImplementation("${mockitoDependency()}")
            }
        """
    }

    def "retries on unnecessary stubbings"() {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeJavaTestSource """
            package acme;

            import org.junit.*;
            import org.junit.runner.*;
            import org.mockito.*;
            import org.mockito.junit.*;


            import static org.mockito.Mockito.*;

            @RunWith(MockitoJUnitRunner.class)
            public class TestWithUnnecessaryStubbings {
                @Mock
                CharSequence s;

                @Before
                public void setup() {
                  when(s.length()).thenReturn(3);
                }

                @Test
                public void someTest() {
                }
            }
        """

        when:
        def result = gradleRunner(gradleVersion).buildAndFail()

        then:
        with(result.output) {
            it.count('TestWithUnnecessaryStubbings > unnecessary Mockito stubbings FAILED') == 2
            !it.contains("unable to retry the following test methods, which is unexpected.")
        }

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }
}
