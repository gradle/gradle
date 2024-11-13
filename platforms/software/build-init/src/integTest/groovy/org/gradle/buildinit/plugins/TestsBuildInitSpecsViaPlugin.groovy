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
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.RepoScriptBlockUtil
import org.gradle.internal.jvm.Jvm
import org.gradle.test.fixtures.plugin.PluginBuilder
import org.hamcrest.Matchers

/**
 * Fixture that provides for publishing a test plugin that adds a custom project spec to a
 * local Maven repository for testing, and contain utilities for asserting that the plugin
 * was resolved and its included spec was loaded.
 */
@SelfType(AbstractIntegrationSpec)
trait TestsBuildInitSpecsViaPlugin {
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

                        ${RepoScriptBlockUtil.gradlePluginRepositoryDefinition()}
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

        pluginBuilder.addSettingsPluginWithCustomCode("""
                java.util.logging.Logger logger = java.util.logging.Logger.getLogger(TestSettingsPlugin.class.getName());
                logger.warning("Applying MyPlugin...")

                org.gradle.buildinit.specs.internal.BuildInitSpecRegistry registry = getBuildInitSpecRegistry()

                registry.register(
                    MyGenerator.class,
                    java.util.Arrays.asList(new MyBuildSpec("first-project-type"), new MyBuildSpec("second-project-type"))
                );

                logger.warning("MyPlugin applied.");
        """, "org.example.myplugin")

        pluginBuilder.java("org/gradle/test/MyBuildSpec.java") << """
            package org.gradle.test;

            import java.util.Arrays;
            import java.util.Collections;
            import java.util.List;

            import org.gradle.buildinit.specs.BuildInitParameter;
            import org.gradle.buildinit.specs.BuildInitSpec;

            public class MyBuildSpec implements BuildInitSpec {
                private final String type;

                public MyBuildSpec(String type) {
                    this.type = type;
                }

                @Override
                public String getType() {
                    return type;
                }

                @Override
                public List<BuildInitParameter<?>> getParameters() {
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
            import org.gradle.buildinit.specs.BuildInitConfig;
            import org.gradle.buildinit.specs.BuildInitGenerator;

            public class MyGenerator implements BuildInitGenerator {
                @Override
                public void generate(BuildInitConfig config, Directory location) {
                    try {
                        File output = location.file("project.output").getAsFile();
                        output.createNewFile();
                        FileWriter writer = new FileWriter(output);
                        writer.write("MyGenerator created this " + config.getBuildSpec().getDisplayName() + " project.");
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
        outputContains("Resolved plugin [id: '$id', version: '$version', apply: true]")
    }

    void assertLoadedSpec(String specName, String type) {
        outputContains("Loaded project spec: '" + specName + " (" + type + ")'")
    }

    void assertProjectFileGenerated(String fileName, String content) {
        def projectFile = file("new-project/$fileName")
        projectFile.assertExists()
        projectFile.assertContents(Matchers.equalTo(content))
    }

    void canBuildGeneratedProject(Jvm jvm = AvailableJavaHomes.getAvailableJdks(JavaVersion.current()).get(0)) {
        def generatedProjectDir = file("new-project")
        executer.usingProjectDirectory(generatedProjectDir)
        executer.withJvm(jvm)

        succeeds("build")
    }
}
