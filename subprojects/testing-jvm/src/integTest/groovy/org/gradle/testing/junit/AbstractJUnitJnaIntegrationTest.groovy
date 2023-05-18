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

package org.gradle.testing.junit

import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.testing.fixture.AbstractTestingMultiVersionIntegrationTest

abstract class AbstractJUnitJnaIntegrationTest extends AbstractTestingMultiVersionIntegrationTest {
    @Requires(UnitTestPreconditions.Windows)
    def canRunTestsUsingJna() {
        given:
        file('src/test/java/OkTest.java') << """
            ${testFrameworkImports}
            import com.sun.jna.platform.win32.Shell32;

            public class OkTest {
                @Test
                public void ok() {
                    assert Shell32.INSTANCE != null;
                }
            }
        """.stripIndent()
        buildFile << """
            apply plugin: 'java'

            ${mavenCentralRepository()}

            dependencies {
                ${testFrameworkDependencies}
                testImplementation 'net.java.dev.jna:jna-platform:4.1.0'
            }
            test.${configureTestFramework}
        """.stripIndent()

        when:
        executer.withTasks('build').run()

        then:
        DefaultTestExecutionResult result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('OkTest')
        result.testClass('OkTest').assertTestPassed('ok')
    }
}
