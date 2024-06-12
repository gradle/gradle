/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.buildinit.plugins

import org.gradle.buildinit.plugins.fixtures.ScriptDslFixture

import static org.hamcrest.CoreMatchers.allOf
import static org.hamcrest.CoreMatchers.containsString

class BuildInitPluginTemplatesIntegrationTest extends AbstractInitIntegrationSpec {
    private File initFile = temporaryFolder.createFile("initscripts/init.gradle")

    @Override
    String subprojectName() { 'app' }

    def "builds project from specified template"() {
        given:
        useTestDirectoryThatIsNotEmbeddedInAnotherBuild()
        applyTemplatingPluginViaInitScript()
        ScriptDslFixture.of(scriptDsl, targetDir, null)

        when:
        runInitWith scriptDsl, '--template', 'https://github.com/gradle/declarative-gradle-kotlin-jvm-template'

        then:
        outputContains("Generating project from template at: https://github.com/gradle/declarative-gradle-kotlin-jvm-template")
        assertTemplatesPluginApplied()

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }

    def "fails if --template specified and templates plugin is not applied"() {
        given:
        useTestDirectoryThatIsNotEmbeddedInAnotherBuild()
        ScriptDslFixture.of(scriptDsl, targetDir, null)

        when:
        initFailsWith scriptDsl, '--template', 'https://github.com/gradle/declarative-gradle-kotlin-jvm-template'

        then:
        result.hasErrorOutput("Template-based project generation requires the 'GradleTemplatesPlugin' plugin to be applied via an init script.")

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }

    def "fails if --template specified without an argument"() {
        given:
        useTestDirectoryThatIsNotEmbeddedInAnotherBuild()
        ScriptDslFixture.of(scriptDsl, targetDir, null)

        when:
        initFailsWith scriptDsl, '--template'

        then:
        result.hasErrorOutput("No argument was provided for command-line option '--template' with description: 'Supply a git repository containing a template to use to generate the project.'")

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }

    /**
     * {@see BuildInitPluginIntegrationTest#"creates a simple project with #scriptDsl build scripts when no pom file present and no type specified"}
     */
    def "applying templates plugin without specifying --template retains original init behavior"() {
        given:
        useTestDirectoryThatIsNotEmbeddedInAnotherBuild()
        applyTemplatingPluginViaInitScript()
        def dslFixture = ScriptDslFixture.of(scriptDsl, targetDir, null)

        when:
        runInitWith scriptDsl

        then:
        commonFilesGenerated(scriptDsl, dslFixture)
        assertTemplatesPluginApplied()

        and:
        dslFixture.buildFile.assertContents(
            allOf(
                containsString("This is a general purpose Gradle build"),
                containsString(documentationRegistry.getSampleForMessage())
            )
        )

        expect:
        succeeds 'properties'

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }

    private void applyTemplatingPluginViaInitScript() {
        initFile << """
            initscript {
                repositories {
                    gradlePluginPortal()
                }
                dependencies {
                    // TODO: A real version of this init script would apply the actual published Templates plugin by id here
                }
            }

            // TODO: But to easily demo the approach, we'll just define a plugin with the same structure here
            apply plugin: GradleTemplatesPlugin

            class GradleTemplatesPlugin implements Plugin<Gradle> {
                void apply(Gradle gradle) {
                    gradle.lifecycle.afterProject { project ->
                        if (project == rootProject) {
                            project.logger.lifecycle("Applying plugin GradleTemplatesPlugin")

                            /*
                             * TODO: This is where the real Templates plugin will do its work - it will add a TaskAction
                             * to the existing init task that will generate the project from a template IFF the
                             * --template argument is provided.  Otherwise, business as usual using the existing
                             * procedural generation.
                             *
                             * This will keep the changes to the existing init task and Gradle project to a minimum and
                             * should be compatible with CC and Project Isolation.
                             */
                            project.tasks.withType(InitBuild).configureEach { task ->
                                task.doLast {
                                    if (task.templateUrl.isPresent()) {
                                        project.logger.lifecycle("Generating project from template at: \${templateUrl.get()}")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        """

        executer.usingInitScript(initFile)
    }

    private void assertTemplatesPluginApplied() {
        outputContains("Applying plugin GradleTemplatesPlugin")
    }
}
