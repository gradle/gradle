/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.testkit.runner.enduser

import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.testkit.runner.BaseGradleRunnerIntegrationTest
import org.gradle.testkit.runner.fixtures.NonCrossVersion
import org.gradle.util.UsesNativeServices
import org.gradle.util.internal.TextUtil

@NonCrossVersion
@UsesNativeServices
@Requires(value = IntegTestPreconditions.NotEmbeddedExecutor, reason = BaseTestKitEndUserIntegrationTest.NOT_EMBEDDED_REASON)
class GradleRunnerDefaultTestKitDirIntegrationTest extends BaseGradleRunnerIntegrationTest implements TestKitDependencyBlock {

    def setup() {
        executer.beforeExecute {
            usingInitScript(file("tempDirInit.gradle") << """
                allprojects {
                    tasks.withType(Test) {
                        // Do not set the DefaultGradleRunner.TEST_KIT_DIR_SYS_PROP system property, that will defeat the purpose of this test
                        systemProperty "java.io.tmpdir", "${TextUtil.normaliseFileSeparators(file("tmp").createDir().absolutePath)}"
                    }
                }
            """)
        }

        buildFile << """
            apply plugin: 'groovy'

            dependencies {
                implementation localGroovy()
            }

            ${mavenCentralRepository()}

            testing {
                suites {
                    test {
                        useSpock()
                    }
                }
            }

            tasks.withType(Test).configureEach {
                testLogging.exceptionFormat = 'full'
                testLogging.showStandardStreams = true
                testLogging.events "started", "skipped", "failed", "passed", "standard_out", "standard_error"
            }
        """
    }


    def "uses a test kit directory under the project build directory by default"() {
        when:
        buildFile << gradleTestKitDependency()

        def workerTmpDir = file("build/tmp/test/work")
        groovyTestSourceFile("""
            import org.gradle.testkit.runner.GradleRunner
            import org.gradle.testkit.runner.internal.DefaultGradleRunner
            import spock.lang.Specification

            class Test extends Specification {
                def "default test kit provider value is derived from system property"() {
                    when:
                    def runner = GradleRunner.create() as DefaultGradleRunner
                    def dir = runner.testKitDirProvider.dir
                    then:
                    dir.toPath().startsWith("${TextUtil.normaliseFileSeparators(workerTmpDir.absolutePath)}")
                }
            }
        """)

        then:
        succeeds 'test'
    }

    def "gradle test kit directory can be changed by the user"() {
        when:
        buildFile << gradleTestKitDependency() << '''
        test {
            File customTestKitDir = file('my-custom-testkit-dir')
            systemProperty('org.gradle.testkit.dir', customTestKitDir)
        }
        '''

        def customTestKitDir = file("my-custom-testkit-dir")
        groovyTestSourceFile("""
            import org.gradle.testkit.runner.GradleRunner
            import org.gradle.testkit.runner.internal.DefaultGradleRunner
            import java.io.File
            import spock.lang.Specification

            class Test extends Specification {

                def "the test kit system property is set by default"() {
                    expect:
                    new File(System.getProperty(DefaultGradleRunner.TEST_KIT_DIR_SYS_PROP)) == new File("${TextUtil.normaliseFileSeparators(customTestKitDir.absolutePath)}")
                }

                def "default test kit provider value is derived from system property"() {
                    when:
                    def runner = GradleRunner.create() as DefaultGradleRunner
                    def dir = runner.testKitDirProvider.dir
                    then:
                    dir == new File("${TextUtil.normaliseFileSeparators(customTestKitDir.absolutePath)}")
                }
            }
        """)

        then:
        succeeds 'test'
    }
}
