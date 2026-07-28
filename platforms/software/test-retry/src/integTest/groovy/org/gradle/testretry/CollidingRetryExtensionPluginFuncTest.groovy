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
        buildFile.text = baseBuildScriptWithNotAppliedTestRetryPlugin()
        applySomePluginWhichAddsRetryExtensionForClass("com.gradle.enterprise.testretry.TestRetryExtension")
        applyTestRetryPlugin()

        when:
        def result = gradleRunner(gradleVersion).buildAndFail()

        then:
        result.output.contains("The Develocity Gradle plugin is conflicting with the Test Retry Gradle plugin")

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    def "detects existing retry extension from some other Gradle plugin"() {
        given:
        buildFile.text = baseBuildScriptWithNotAppliedTestRetryPlugin()
        applySomePluginWhichAddsRetryExtensionForClass("java.lang.Object")
        applyTestRetryPlugin()

        when:
        def result = gradleRunner(gradleVersion).buildAndFail()

        then:
        result.output.contains("Another plugin is conflicting with the Test Retry Gradle plugin")

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    void buildSrcWithEmptyClass(String packageName, String className) {
        def buildSrc = new FileTreeBuilder(testProjectDir.newFolder("buildSrc"))

        buildSrc {
            file("build.gradle")
            dir("src/main/java") {
                dir(packageName.replace('.', '/')) {
                    file("${className}.java") {
                        text = """
                            package ${packageName};
                            public class ${className} {
                            }
                        """.stripMargin()
                    }
                }
            }

        }
    }

    void applyTestRetryPlugin() {
        buildFile << """
            apply plugin: "org.gradle.test-retry"
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
