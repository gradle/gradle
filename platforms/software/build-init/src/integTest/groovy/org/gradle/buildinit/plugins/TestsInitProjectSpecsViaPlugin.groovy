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

import groovy.transform.SelfType
import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.RepoScriptBlockUtil
import org.gradle.internal.jvm.Jvm
import org.gradle.test.fixtures.plugin.PluginBuilder

/**
 * Fixture that provides for publishing a test plugin that adds a custom project spec to a
 * local Maven repository for testing, and contain utilities for asserting that the plugin
 * was resolved and its included spec was loaded.
 */
@SelfType(AbstractInitIntegrationSpec)
trait TestsInitProjectSpecsViaPlugin {
    def setup() {
        setupRepositoriesViaInit()
    }

    private void setupRepositoriesViaInit() {
        groovyFile("init.gradle", """
            beforeSettings { settings ->
                settings.pluginManagement {
                    repositories {
                        maven {
                            url '${mavenRepo.uri}'
                        }
                        ${RepoScriptBlockUtil.googleRepositoryDefinition()} // For AGP, needed by D-G prototype
                        ${RepoScriptBlockUtil.gradlePluginRepositoryDefinition()}
                    }
                }
            }
        """)
    }

    void publishTestPlugin() {
        def pluginProjectDir = file("plugin").createDir()
        executer.usingProjectDirectory(pluginProjectDir)

        PluginBuilder pluginBuilder = buildTestPlugin()

        executer.requireOwnGradleUserHomeDir("Adding new API that plugin needs") // TODO: Remove this when API is solid enough that it isn't changing every test run (it slows down test running)
        executer.withArgument("--stacktrace")
        def results = pluginBuilder.publishAs("org.example.myplugin:plugin:1.0", mavenRepo, executer)

        println()
        println "Published: '${results.getPluginModule().with { m -> m.getGroup() + ':' + m.getModule() + ':' + m.getVersion() }}'"
        println "To: '${mavenRepo.uri}'"
    }

    private PluginBuilder buildTestPlugin() {
        def pluginBuilder = new PluginBuilder(testDirectory.file("plugin"))

        pluginBuilder.addSettingsPlugin("""
    println("MyPlugin applied.")
    settings.initProjectSpecRegistry.register("First Project Type", org.gradle.test.MyGenerator, org.gradle.test.MyGeneratorParameters) {
        message = "First type"
    }
    settings.initProjectSpecRegistry.register("Second Project Type", org.gradle.test.MyGenerator, org.gradle.test.MyGeneratorParameters) {
        message = "Second type"
    }
""", "org.example.myplugin")

        pluginBuilder.java("org/gradle/test/MyGeneratorParameters.java").java("""
            package org.gradle.test;

            import org.gradle.api.provider.Property;
            import org.gradle.buildinit.projectspecs.InitParameters;

            public interface MyGeneratorParameters extends InitParameters {
                Property<String> getMessage();
            }
        """)

        pluginBuilder.java("org/gradle/test/MyGenerator.java").java("""
            package org.gradle.test;

            import java.io.File;
            import java.io.FileWriter;
            import java.io.IOException;

            import org.gradle.api.file.Directory;
            import org.gradle.buildinit.projectspecs.InitAction;

            public abstract class MyGenerator implements InitAction<MyGeneratorParameters> {
                @Override
                public void execute() {
                    Directory projectDir = getParameters().getProjectDirectory().get();
                    try {
                        File outputFile = projectDir.file("project.output").getAsFile();
                        outputFile.createNewFile();
                        FileWriter writer = new FileWriter(outputFile);
                        writer.write("MyGenerator says " + getParameters().getMessage().get());
                        writer.close();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        """)

        return pluginBuilder
    }

    void assertResolvedPlugin(String id, String version) {
        outputContains("Resolved plugin [id: '$id', version: '$version', apply: false]")
    }

    void assertLoadedSpec(String specName, String type) {
        outputContains("Loaded project spec: '" + specName + " (" + type + ")'")
    }

    void assertProjectFileGenerated(String fileName, String content) {
        def projectFile = file("new-project/$fileName")
        assert projectFile.exists(), "Project file '$fileName' does not exist."
        assert projectFile.text == content
    }

    void canBuildGeneratedProject(Jvm jvm = AvailableJavaHomes.getAvailableJdks(JavaVersion.current()).get(0)) {
        def generatedProjectDir = file("new-project")
        executer.usingProjectDirectory(generatedProjectDir)
        executer.withJvm(jvm)

        succeeds("build")
    }
}
