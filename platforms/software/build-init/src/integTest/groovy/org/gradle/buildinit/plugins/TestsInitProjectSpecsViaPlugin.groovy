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

import org.gradle.test.fixtures.plugin.PluginBuilder

/**
 * Fixture that provides for publishing a test plugin that adds a custom project spec to a
 * local Maven repository for testing, and contain utilities for asserting that the plugin
 * was resolved and its included spec was loaded.
 */
trait TestsInitProjectSpecsViaPlugin {
    def setup() {
        setupRepositoriesViaInit()
    }

    private void setupRepositoriesViaInit() {
        groovyFile("init.gradle", """
            settingsEvaluated { settings ->
                settings.pluginManagement {
                    repositories {
                        maven {
                            url '${mavenRepo.uri}'
                        }
                        google() // For AGP, needed by D-G prototype
                        gradlePluginPortal()
                    }
                }
            }
        """)
    }

    void publishTestPlugin() {
        def pluginProjectDir = file("plugin").with { createDir() }
        executer.usingProjectDirectory(pluginProjectDir)

        PluginBuilder pluginBuilder = buildTestPlugin()

        executer.requireOwnGradleUserHomeDir("Adding new API that plugin needs") // TODO: Remove this when API is solid enough that it isn't changing every test run (it slows down test running)
        def results = pluginBuilder.publishAs("org.example.myplugin:plugin:1.0", mavenRepo, executer)

        println()
        println "Published: '${results.getPluginModule().with { m -> m.getGroup() + ':' + m.getModule() + ':' + m.getVersion() }}'"
        println "To: '${mavenRepo.uri}'"
    }

    private PluginBuilder buildTestPlugin() {
        def pluginBuilder = new PluginBuilder(testDirectory.file("plugin"))

        pluginBuilder.addPluginWithCustomCode("""
                project.getLogger().lifecycle("MyPlugin applied.");
        """, "org.example.myplugin")

        pluginBuilder.file("src/main/resources/META-INF/services/org.gradle.buildinit.projectspecs.InitProjectSource") << "org.gradle.test.MySource\n"

        pluginBuilder.java("org/gradle/test/MySource.java") << """
            package org.gradle.test;

            import java.util.Arrays;
            import java.util.Collections;
            import java.util.List;

            import org.gradle.buildinit.projectspecs.InitProjectGenerator;
            import org.gradle.buildinit.projectspecs.InitProjectParameter;
            import org.gradle.buildinit.projectspecs.InitProjectSpec;
            import org.gradle.buildinit.projectspecs.InitProjectSource;

            public class MySource implements InitProjectSource {
                @Override
                public List<InitProjectSpec> getProjectSpecs() {
                    return Arrays.asList(
                        new MyProjectSpec("Custom Project Type"),
                        new MyProjectSpec("Custom Project Type 2")
                    );
                }

                @Override
                public Class<? extends InitProjectGenerator> getProjectGenerator() {
                    return MyGenerator.class;
                }
            }
        """

        pluginBuilder.java("org/gradle/test/MyProjectSpec.java") << """
            package org.gradle.test;

            import java.util.Arrays;
            import java.util.Collections;
            import java.util.List;

            import org.gradle.buildinit.projectspecs.InitProjectParameter;
            import org.gradle.buildinit.projectspecs.InitProjectSpec;

            public class MyProjectSpec implements InitProjectSpec {
                private final String displayName;

                public MyProjectSpec(String displayName) {
                    this.displayName = displayName;
                }

                @Override
                public String getDisplayName() {
                    return displayName;
                }

                @Override
                public List<InitProjectParameter<?>> getParameters() {
                    return Collections.emptyList();
                }
            }
        """

        pluginBuilder.java("org/gradle/test/MyGenerator.java") << """
            package org.gradle.test;

            import java.io.File;
            import java.io.FileWriter;
            import java.io.IOException;

            import org.gradle.api.file.Directory;
            import org.gradle.buildinit.projectspecs.InitProjectConfig;
            import org.gradle.buildinit.projectspecs.InitProjectGenerator;

            public class MyGenerator implements InitProjectGenerator {
                @Override
                public void generate(InitProjectConfig config, Directory location) {
                    try {
                        File output = location.file("project.output").getAsFile();
                        output.createNewFile();
                        FileWriter writer = new FileWriter(output);
                        writer.write("MyGenerator created this " + config.getProjectSpec().getDisplayName() + " project.");
                        writer.close();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        """

        return pluginBuilder
    }

    void assertResolvedPlugin(String id, String version) {
        outputContains("Resolved plugin [id: '$id', version: '$version', apply: false]")
    }

    void assertLoadedSpec(String specName) {
        outputContains("Loaded project spec: '" + specName + "'")
    }

    void assertProjectFileGenerated(String fileName, String content) {
        def projectFile = file("new-project/$fileName")
        assert projectFile.exists(), "Project file '$fileName' does not exist."
        assert projectFile.text == content
    }
}
