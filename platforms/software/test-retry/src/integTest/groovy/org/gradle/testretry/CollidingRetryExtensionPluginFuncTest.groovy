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
package org.gradle.testretry

class CollidingRetryExtensionPluginFuncTest extends AbstractGeneralPluginFuncTest {

    def "detects existing retry extension from Develocity Gradle plugin"() {
        given:
        buildSrcWithEmptyClass("com.gradle.enterprise.testretry", "TestRetryExtension")

        and:
        buildFile.text = buildScriptWithoutRetryPlugin()
        applySomePluginWhichAddsRetryExtensionForClass("com.gradle.enterprise.testretry.TestRetryExtension")
        applyTestRetryPlugin()

        when:
        fails('test')

        then:
        failure.assertHasCause("The Develocity Gradle plugin is conflicting with the Test Retry Gradle plugin")
    }

    def "detects existing retry extension from some other Gradle plugin"() {
        given:
        buildFile.text = buildScriptWithoutRetryPlugin()
        applySomePluginWhichAddsRetryExtensionForClass("java.lang.Object")
        applyTestRetryPlugin()

        when:
        fails('test')

        then:
        failure.assertHasCause("Another plugin is conflicting with the Test Retry Gradle plugin")
    }

    /**
     * Build script with the java plugin applied but the retry plugin NOT yet applied.
     * Uses legacy apply() instead of the plugins {} block to avoid the "apply false" restriction
     * on core/bundled plugins.
     */
    String buildScriptWithoutRetryPlugin() {
        return """
            apply plugin: 'java'

            ${mavenCentralRepository()}

            ${buildConfiguration()}

            tasks.named("test").configure {
                testLogging {
                    events "passed", "skipped", "failed"
                }
            }
        """
    }

    void buildSrcWithEmptyClass(String packageName, String className) {
        file("buildSrc/build.gradle").createFile()
        file("buildSrc/src/main/java/${packageName.replace('.', '/')}/${className}.java").text = """
            package ${packageName};
            public class ${className} {
            }
        """
    }

    void applyTestRetryPlugin() {
        buildFile << """
            apply plugin: "org.gradle.test-retry-bundled"
        """
    }

    void applySomePluginWhichAddsRetryExtensionForClass(String extensionClassName) {
        buildFile << """
            class SomePlugin implements Plugin<Project> {
                void apply(Project target) {
                    target.tasks.withType(Test).configureEach {
                        extensions.add("retry", new ${extensionClassName}())
                    }
                }
            }

            apply plugin: SomePlugin
        """

    }
}
