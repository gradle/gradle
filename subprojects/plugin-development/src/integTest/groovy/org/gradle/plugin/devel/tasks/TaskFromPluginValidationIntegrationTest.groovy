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

package org.gradle.plugin.devel.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.GroovyBuildScriptLanguage
import org.gradle.internal.reflect.problems.ValidationProblemId
import org.gradle.internal.reflect.validation.ValidationMessageChecker
import org.gradle.internal.reflect.validation.ValidationTestFor
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions

@Requires(IntegTestPreconditions.IsEmbeddedExecutor)
// this test only works in embedded mode because of the use of validation test fixtures
class TaskFromPluginValidationIntegrationTest extends AbstractIntegrationSpec implements ValidationMessageChecker {

    def setup() {
        expectReindentedValidationMessage()
    }

    @ValidationTestFor(
        ValidationProblemId.TEST_PROBLEM
    )
    def "detects that a problem is from a task declared in a precompiled script plugin"() {
        withPrecompiledScriptPlugins()
        def pluginFile = file("buildSrc/src/main/groovy/test.gradle.demo.plugin.gradle")
        writeTaskInto(pluginFile)
        pluginFile << """
            tasks.register("myTask", SomeTask) {
                input.set("hello")
                output.set(layout.buildDirectory.file("out.txt"))
            }
        """

        buildFile """plugins {
            id 'test.gradle.demo.plugin'
        }"""

        when:
        fails ':myTask'

        then:
        failureDescriptionContains(dummyValidationProblem {
            inPlugin('test.gradle.demo.plugin')
            type('SomeTask').property('input')
        }.trim())
    }

    @ValidationTestFor(
        ValidationProblemId.TEST_PROBLEM
    )
    def "detects that a problem is from a task declared in plugin"() {
        settingsFile << """
            includeBuild 'my-plugin'
        """
        copyValidationProblemClass()
        def pluginFile = file("my-plugin/src/main/groovy/org/gradle/demo/plugin/MyTask.groovy")
        writeTaskInto("""package org.gradle.demo.plugin

            import org.gradle.api.*
            import org.gradle.api.file.*
            import org.gradle.api.provider.*
            import org.gradle.api.tasks.*
        """, pluginFile)

        file("my-plugin/build.gradle") << """
            plugins {
                id 'groovy-gradle-plugin'
            }

            gradlePlugin {
                plugins {
                    create("simplePlugin") {
                        id = "org.gradle.demo.plugin"
                        implementationClass = "org.gradle.demo.plugin.MyPlugin"
                    }
                }
            }
        """
        file("my-plugin/settings.gradle") << """
            rootProject.name = "my-plugin"
        """
        file("my-plugin/src/main/groovy/org/gradle/demo/plugin/MyPlugin.groovy") << """package org.gradle.demo.plugin
            import org.gradle.api.*
            class MyPlugin implements Plugin<Project> {
                void apply(Project p) {
                    p.tasks.register("myTask", SomeTask) {
                        it.input.set("hello")
                        it.output.set(p.layout.buildDirectory.file("out.txt"))
                    }
                }
            }
        """

        buildFile """plugins {
            id 'org.gradle.demo.plugin'
        }"""

        when:
        fails ':myTask'

        then:
        failureDescriptionContains(dummyValidationProblem {
            inPlugin('org.gradle.demo.plugin')
            type('org.gradle.demo.plugin.SomeTask').property('input')
        })
    }

    /**
     * This method creates a dummy ValidationProblem class in the plugin source set, because
     * the test fixture is not visible at compile time for included builds like they are
     * typically for precompiled script plugins.
     * This is really a workaround for the test setup, which doesn't bring any value to the
     * test itself and therefore is separated for readability.
     */
    private void copyValidationProblemClass() {
        file("my-plugin/src/main/groovy/org/gradle/integtests/fixtures/validation/ValidationProblem.groovy") << """
package org.gradle.integtests.fixtures.validation;

import org.gradle.api.problems.Severity;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A dummy annotation which is used to trigger validation problems
 * during tests
 */
@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.METHOD, ElementType.FIELD])
public @interface ValidationProblem {
    Severity value() default Severity.WARNING;
}
        """
    }

    private TestFile withPrecompiledScriptPlugins() {
        file("buildSrc/build.gradle") << """
            plugins {
                id 'groovy-gradle-plugin'
            }
        """
    }

    private void writeTaskInto(@GroovyBuildScriptLanguage String header = "", TestFile testFile) {
        testFile << """$header
            import org.gradle.integtests.fixtures.validation.ValidationProblem
            import org.gradle.api.problems.Severity

            abstract class SomeTask extends DefaultTask {
                @ValidationProblem(value=Severity.ERROR)
                abstract Property<String> getInput()

                @OutputFile
                abstract RegularFileProperty getOutput()

                @TaskAction
                void doSomething() {}
            }
        """
    }
}
