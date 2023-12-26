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

package gradlebuild.integrationtests.ide

import gradlebuild.basics.accessors.groovy
import gradlebuild.integrationtests.addDependenciesAndConfigurations
import gradlebuild.integrationtests.tasks.SmokeIdeTest
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.kotlin.dsl.*
import org.gradle.plugins.ide.idea.IdeaPlugin
import org.gradle.process.CommandLineArgumentProvider

class SmokeIdeTestPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        configureIdeProvisioning()
        val sourceSet = createSmokeIdeTestSourceSet()
        val jvmArgumentProvider = the<AndroidStudioProvisioningExtension>().androidStudioSystemProperties(this, emptyList())
        createSmokeIdeTestTask(sourceSet, jvmArgumentProvider)
        addDependenciesAndConfigurations("smokeIde")
    }

    private
    fun Project.createSmokeIdeTestSourceSet(): SourceSet = the<SourceSetContainer>().run {
        val main by getting
        val smokeIdeTest by creating {
            compileClasspath += main.output
            runtimeClasspath += main.output
        }

        plugins.withType<IdeaPlugin> {
            with(model) {
                module {
                    testSources.from(smokeIdeTest.java.srcDirs, smokeIdeTest.groovy.srcDirs)
                    testResources.from(smokeIdeTest.resources.srcDirs)
                }
            }
        }

        smokeIdeTest
    }

    private
    fun Project.createSmokeIdeTestTask(sourceSet: SourceSet, jvmArgumentProvider: CommandLineArgumentProvider) {
        tasks.register<SmokeIdeTest>("smokeIdeTest") {
            group = "Verification"
            maxParallelForks = 1
            systemProperties["org.gradle.integtest.executer"] = "forking"
            testClassesDirs = sourceSet.output.classesDirs
            classpath = sourceSet.runtimeClasspath
            jvmArgumentProviders.add(jvmArgumentProvider)
        }
    }

    private
    fun Project.configureIdeProvisioning() {
        pluginManager.apply(AndroidStudioProvisioningPlugin::class)
    }
}
