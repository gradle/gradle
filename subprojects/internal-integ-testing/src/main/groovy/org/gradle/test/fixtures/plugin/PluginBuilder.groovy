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

package org.gradle.test.fixtures.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.TextUtil

class PluginBuilder {

    final TestFile projectDir

    String packageName = "org.gradle.test"

    final Map<String, String> pluginIds = [:]

    PluginBuilder(TestFile projectDir) {
        this.projectDir = projectDir
    }

    TestFile file(String path) {
        projectDir.file(path)
    }

    TestFile groovy(String path) {
        file("src/main/groovy/${packageName.replaceAll("\\.", "/")}/$path")
    }

    @SuppressWarnings("GrMethodMayBeStatic")
    String generateManagedBuildScript() {
        """
            apply plugin: "groovy"
            dependencies {
              compile localGroovy()
              compile gradleApi()
            }
        """
    }

    String generateBuildScript(String additions = "") {
        file("build.gradle").text = (generateManagedBuildScript() + additions)
    }

    void publishTo(GradleExecuter executer, TestFile testFile) {
        generateBuildScript """
            jar {
                archiveName = "$testFile.name"
                destinationDir = file("${TextUtil.escapeString(testFile.parentFile.absolutePath)}")
            }
        """

        writePluginDescriptors(pluginIds)
        executer.inDirectory(projectDir).withTasks("jar").run()
    }

    void generateForBuildSrc() {
        generateBuildScript()
        writePluginDescriptors(pluginIds)
    }

    protected void writePluginDescriptors(Map<String, String> pluginIds) {
        descriptorsDir.deleteDir()
        pluginIds.each { id, className ->
            descriptorsDir.file("${id}.properties") << "implementation-class=${packageName}.${className}"
        }
    }

    TestFile getDescriptorsDir() {
        file("src/main/resources/META-INF/gradle-plugins")
    }

    PluginBuilder addPlugin(String impl, String id = "test-plugin", String className = "TestPlugin") {
        pluginIds[id] = className

        groovy("${className}.groovy") << """
            package $packageName

            class $className implements $Plugin.name<$Project.name> {
                void apply($Project.name project) {
                    $impl
                }
            }
        """
        this
    }

    PluginBuilder addUnloadablePlugin(String id = "test-plugin", String className = "TestPlugin") {
        pluginIds[id] = className

        groovy("${className}.groovy") << """
            package $packageName

            class $className implements $Plugin.name<$Project.name> {
                static { throw new Exception("unloadable plugin class") }
                void apply($Project.name project) {
                }
            }
        """
        this
    }

    PluginBuilder addPluginWithPrintlnTask(String taskName, String message, String id = "test-plugin", String className = "TestPlugin") {
        addPlugin("project.task(\"$taskName\") << { println \"$message\" }", id, className)
        this
    }
}
