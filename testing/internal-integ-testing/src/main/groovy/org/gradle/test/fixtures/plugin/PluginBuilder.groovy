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

import com.google.common.base.Splitter
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.initialization.Settings
import org.gradle.integtests.fixtures.GroovyBuildScriptLanguage
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.model.ModelMap
import org.gradle.model.Mutate
import org.gradle.model.RuleSource
import org.gradle.test.fixtures.HttpModule
import org.gradle.test.fixtures.Module
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.ivy.IvyRepository
import org.gradle.test.fixtures.maven.MavenRepository
import org.gradle.test.fixtures.server.http.MavenHttpPluginRepository
import org.gradle.util.internal.TextUtil

class PluginBuilder {
    static final String PLUGIN_MARKER_SUFFIX = ".gradle.plugin";

    final TestFile projectDir

    String packageName = "org.gradle.test"

    final Map<String, String> pluginIds = [:]

    PluginBuilder(TestFile projectDir) {
        this.projectDir = projectDir
    }

    TestFile getBuildFile() {
        file("build.gradle")
    }

    TestFile file(String path) {
        projectDir.file(path)
    }

    TestFile groovy(String path) {
        file("src/main/groovy/${sourceFilePath(path)}")
    }

    TestFile java(String path) {
        file("src/main/java/${sourceFilePath(path)}")
    }

    private String sourceFilePath(String path) {
        packageName ? "${packageName.replaceAll("\\.", "/")}/$path" : path
    }

    @SuppressWarnings("GrMethodMayBeStatic")
    String generateManagedBuildScript() {
        """
            apply plugin: "java-gradle-plugin"
            apply plugin: "groovy"
            dependencies {
              implementation localGroovy()
            }
            group = "${packageName}"
            version = "1.0"
        """
    }

    void prepareToExecute() {
        buildFile << generateManagedBuildScript()
        buildFile << getPluginDescriptors(pluginIds)
        projectDir.file('settings.gradle').write("")
    }

    void publishTo(GradleExecuter executer, TestFile testFile, String buildScript = "") {
        prepareToExecute()
        buildFile << buildScript
        buildFile << """
            jar {
                archiveFileName = "$testFile.name"
                destinationDirectory = file("${TextUtil.escapeString(testFile.parentFile.absolutePath)}")
            }
        """
        executer.inDirectory(projectDir).withTasks("jar").run()
    }

    PluginPublicationResults publishAs(String coordinates, MavenRepository mavenRepo, GradleExecuter executer) {
        List<String> gav = Splitter.on(":").splitToList(coordinates)
        return publishAs(gav.get(0), gav.get(1), gav.get(2), mavenRepo, executer)
    }

    PluginHttpPublicationResults publishAs(String group, String artifact, String version, MavenHttpPluginRepository mavenRepo, GradleExecuter executer) {
        return new PluginHttpPublicationResults(publishAs(group, artifact, version, mavenRepo as MavenRepository, executer))
    }

    PluginPublicationResults publishAs(String group, String artifact, String version, MavenRepository mavenRepo, GradleExecuter executer) {

        // The implementation jar module.
        def module = mavenRepo.module(group, artifact, version)
        def pluginModule = module.publish()
        def artifactFile = module.getArtifactFile()

        def markerModules = new ArrayList<Module>()

        pluginIds.keySet().each { id ->
            // The marker files for each plugin.
            def marker = mavenRepo.module(id, id + PLUGIN_MARKER_SUFFIX, version)
            marker.dependsOn(module)
            markerModules.add(marker.publish())
        }

        publishTo(executer, artifactFile)

        return new PluginPublicationResults(pluginModule, markerModules)
    }

    PluginPublicationResults publishAs(String coordinates, IvyRepository ivyRepo, GradleExecuter executer) {
        List<String> omr = Splitter.on(":").splitToList(coordinates)

        // The implementation jar module.
        def module = ivyRepo.module(omr.get(0), omr.get(1), omr.get(2))
        def artifactFile = module.artifact([:]).getJarFile()
        module.publish()

        def markerModules = new ArrayList<Module>()

        pluginIds.keySet().each { id ->
            // The marker files for each plugin.
            def marker = ivyRepo.module(id, id + PLUGIN_MARKER_SUFFIX, omr[2])
            marker.dependsOn(module)
            marker.publish()
            markerModules.add(marker)
        }

        publishTo(executer, artifactFile);

        return new PluginPublicationResults(module, markerModules)
    }

    void generateForBuildSrc() {
        prepareToExecute()
    }

    protected String getPluginDescriptors(Map<String, String> pluginIds) {
        return """
            gradlePlugin {
                plugins {
                    ${pluginIds.collect { id, className ->
                        "'${id}' { id='${id}'; implementationClass='${packageName}.${className}' }"
                    }.join("\n")}
                }
            }
        """
    }

    PluginBuilder addPluginSource(String id, String className, @GroovyBuildScriptLanguage String impl) {
        addPluginId(id, className)
        groovy("${className}.groovy") << impl
        this
    }

    PluginBuilder addPluginId(String id, String className) {
        pluginIds[id] = className
        this
    }

    PluginBuilder addPlugin(String impl, String id = "test-plugin", String className = "TestPlugin") {
        addPluginSource(id, className, """
            ${packageName ? "package $packageName" : ""}

            class $className implements $Plugin.name<$Project.name> {
                void apply($Project.name project) {
                    $impl
                }
            }
        """)
        this
    }

    PluginBuilder addSettingsPlugin(String impl, String id = "test-settings-plugin", String className = "TestSettingsPlugin") {
        addPluginSource(id, className, """
            ${packageName ? "package $packageName" : ""}

            class $className implements $Plugin.name<$Settings.name> {
                void apply($Settings.name settings) {
                    $impl
                }
            }
        """)
        this
    }

    PluginBuilder addUnloadablePlugin(String id = "test-plugin", String className = "TestPlugin") {
        addPluginSource(id, className, """
            ${packageName ? "package $packageName" : ""}

            class $className implements $Plugin.name<$Project.name> {
                static { throw new Exception("unloadable plugin class") }
                void apply($Project.name project) {
                }
            }
        """)
        this
    }

    PluginBuilder addNonConstructiblePlugin(String id = "test-plugin", String className = "TestPlugin") {
        addPluginSource(id, className, """
            ${packageName ? "package $packageName" : ""}

            class $className implements $Plugin.name<$Project.name> {
                $className() { throw new RuntimeException("broken plugin") }
                void apply($Project.name project) {
                }
            }
        """)
        this
    }

    PluginBuilder addPluginWithPrintlnTask(String taskName, String message, String id = "test-plugin", String className = "TestPlugin") {
        addPlugin("project.task(\"$taskName\") { doLast { println \"$message\" } }", id, className)
        this
    }

    PluginBuilder addRuleSource(String pluginId) {
        String className = "TestRuleSource"
        addPluginSource(pluginId, className, """
            ${packageName ? "package $packageName" : ""}

            class $className extends $RuleSource.name {
                @$Mutate.name
                void addTask($ModelMap.name<$Task.name> tasks) {
                    tasks.create("fromModelRule") {
                        it.doLast {
                            println "Model rule provided task executed"
                        }
                    }
                }
            }
        """)
        this
    }

    class PluginPublicationResults {
        final Module pluginModule
        final List<Module> markerModules

        PluginPublicationResults(pluginModule, markerModules) {
            this.pluginModule = pluginModule
            this.markerModules = markerModules
        }
    }

    class PluginHttpPublicationResults extends PluginPublicationResults {
        final HttpModule pluginModule
        final List<HttpModule> markerModules

        PluginHttpPublicationResults(PluginPublicationResults results) {
            super(results.pluginModule, results.markerModules)
            this.pluginModule = results.pluginModule as HttpModule
            this.markerModules = results.markerModules as List<HttpModule>
        }

        PluginHttpPublicationResults allowAll() {
            ([pluginModule] + markerModules)*.allowAll()
            return this
        }
    }
}
