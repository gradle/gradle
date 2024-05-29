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

package org.gradle.performance.generator

import groovy.transform.CompileStatic

import static org.gradle.test.fixtures.dsl.GradleDsl.DECLARATIVE
import static org.gradle.test.fixtures.dsl.GradleDsl.KOTLIN

@CompileStatic
class DeclarativeDslTestProjectGenerator extends AbstractTestProjectGenerator {

    final TestProjectGeneratorConfiguration config

    DeclarativeDslTestProjectGenerator(TestProjectGeneratorConfiguration config) {
        this.config = config

        if (config.dsl != DECLARATIVE) {
            throw new IllegalArgumentException("Template ${config.templateName} only supports the ${DECLARATIVE.name()} DSL")
        }

        if (config.compositeBuild) {
            throw new IllegalArgumentException("Template ${config.templateName} doesn't support composite builds")
        }
    }

    def generate(File outputBaseDir) {
        def rootProjectDir = new File(outputBaseDir, config.projectName)
        rootProjectDir.mkdirs()

        generateEcosystemPlugins(rootProjectDir)
        generateProjects(rootProjectDir)
    }

    def generateProjects(File rootProjectDir) {
        generateProject(rootProjectDir, null)
        for (int subProjectNumber = 0; subProjectNumber < config.subProjects; subProjectNumber++) {
            def subProjectDir = new File(rootProjectDir, "project$subProjectNumber")
            generateProject(subProjectDir, subProjectNumber)
        }
        for (char libId = 'A'; libId <= ('C' as char); libId++) {
            def libProjectDir = new File(rootProjectDir, "lib$libId")
            generateLibProject(libProjectDir)
        }
    }

    def generateProject(File projectDir, Integer subProjectNumber) {
        def isRoot = subProjectNumber == null

        file projectDir, KOTLIN.fileNameFor('settings'), generateSettingsGradle(isRoot)
        file projectDir, "gradle.properties", generateGradleProperties(isRoot)

        file projectDir, config.dsl.fileNameFor('build'), generateBuildGradle(subProjectNumber)
    }

    static def generateLibProject(File projectDir) {
        file projectDir, KOTLIN.fileNameFor('build'), """
            plugins {
                id("java-library")
            }
        """.stripIndent()
    }

    String generateSettingsGradle(boolean isRoot) {
        if (!isRoot) {
            return null
        }

        String includedProjects = """
            pluginManagement {
                includeBuild("ecosystem-plugin")
            }

            plugins {
                id("org.gradle.experimental.jvm-ecosystem")
            }

            rootProject.name = "root-project"

            include(":libA")
            include(":libB")
            include(":libC")

        """.stripIndent()

        if (config.subProjects != 0) {
            includedProjects += """${(0..config.subProjects - 1).collect { "include(\"project$it\")" }.join("\n")}"""
        }

        return includedProjects
    }

    static String generateBuildGradle(Integer subProjectNumber) {
        def isRoot = subProjectNumber == null

        if (isRoot) {
            return "".stripIndent()
        }

        return """
            javaApplication {
                javaVersion = 17
                mainClass = ""

                dependencies {
                    implementation(project(":libA"))
                    implementation(project(":libB"))
                    implementation(project(":libC"))
                }
            }

            // comment to make each file different $subProjectNumber
        """.stripIndent()
    }

    String generateGradleProperties(boolean isRoot) {
        if (!isRoot) {
            return null
        }
        """
        org.gradle.jvmargs=-Xms${config.daemonMemory} -Xmx${config.daemonMemory} -Dfile.encoding=UTF-8
        org.gradle.parallel=${config.parallel}
        org.gradle.workers.max=${config.maxWorkers}
        compilerMemory=${config.compilerMemory}
        testRunnerMemory=${config.testRunnerMemory}
        testForkEvery=${config.testForkEvery}
        ${->
            config.systemProperties.entrySet().collect { "systemProp.${it.key}=${it.value}" }.join("\n")
        }
        """
    }

    def generateEcosystemPlugins(File rootProjectDir) {
        def ecosystemPluginDir = new File(rootProjectDir, 'ecosystem-plugin')
        ecosystemPluginDir.mkdir()

        file ecosystemPluginDir, KOTLIN.fileNameFor('settings'), """
            dependencyResolutionManagement {
                repositories {
                    google()
                    gradlePluginPortal()
                    mavenCentral()
                }
            }

            include("plugin-jvm")
            rootProject.name = "ecosystem-plugin"
        """

        generateJvmPlugin(ecosystemPluginDir)
    }

    def generateJvmPlugin(File ecosystemPluginDir) {
        def jvmPluginDir = new File(ecosystemPluginDir, 'plugin-jvm')
        jvmPluginDir.mkdir()

        file jvmPluginDir, KOTLIN.fileNameFor('build'), """
            plugins {
                `kotlin-dsl`
            }

            description = "Implements the declarative JVM DSL prototype"

            gradlePlugin {
                plugins {
                    create("jvm-ecosystem") {
                        id = "org.gradle.experimental.jvm-ecosystem"
                        displayName = "JVM Ecosystem Experimental Declarative Plugin"
                        description = "Experimental declarative plugin for the JVM ecosystem"
                        implementationClass = "org.gradle.api.experimental.jvm.JvmEcosystemPlugin"
                        tags = setOf("declarative-gradle", "java", "jvm")
                    }
                }
            }
        """

        def jvmSourceDir = new File(jvmPluginDir, 'src/main/java/org/gradle/api/experimental/jvm')
        jvmSourceDir.mkdirs()

        file jvmSourceDir, 'JvmEcosystemPlugin.java', """
            package org.gradle.api.experimental.jvm;

            import org.gradle.api.Plugin;
            import org.gradle.api.experimental.java.StandaloneJavaApplicationPlugin;
            import org.gradle.api.initialization.Settings;
            import org.gradle.api.internal.SettingsInternal;
            import org.gradle.api.internal.plugins.software.RegistersSoftwareTypes;
            import org.gradle.plugin.software.internal.SoftwareTypeRegistry;

            @RegistersSoftwareTypes({
                    StandaloneJavaApplicationPlugin.class,
            })
            public class JvmEcosystemPlugin implements Plugin<Settings> {
                @Override
                public void apply(Settings target) { }
            }
        """

        file jvmSourceDir, 'HasJvmApplication.java', """
            package org.gradle.api.experimental.jvm;

            import org.gradle.api.Action;
            import org.gradle.api.experimental.jvm.ApplicationDependencies;
            import org.gradle.api.provider.Property;
            import org.gradle.api.tasks.Nested;
            import org.gradle.declarative.dsl.model.annotations.Configuring;
            import org.gradle.declarative.dsl.model.annotations.Restricted;

            /**
             * Represents an application that runs on the JVM.
             */
            @Restricted
            public interface HasJvmApplication {
                @Restricted
                Property<String> getMainClass();

                @Nested
                ApplicationDependencies getDependencies();

                @Configuring
                default void dependencies(Action<? super ApplicationDependencies> action) {
                    action.execute(getDependencies());
                }
            }
        """

        file jvmSourceDir, 'HasJavaTarget.java', """
            package org.gradle.api.experimental.jvm;

            import org.gradle.api.provider.Property;
            import org.gradle.declarative.dsl.model.annotations.Restricted;

            /**
             * A component that is built for a single Java version.
             */
            @Restricted
            public interface HasJavaTarget {
                @Restricted
                Property<Integer> getJavaVersion();
            }
        """

        file jvmSourceDir, 'ApplicationDependencies.java', """
            package org.gradle.api.experimental.jvm;

            import org.gradle.api.artifacts.dsl.Dependencies;
            import org.gradle.api.artifacts.dsl.DependencyCollector;
            import org.gradle.declarative.dsl.model.annotations.Restricted;

            /**
             * The declarative dependencies DSL block for an application.
             */
            @SuppressWarnings("UnstableApiUsage")
            @Restricted
            public interface ApplicationDependencies extends Dependencies {
                DependencyCollector getImplementation();
                DependencyCollector getRuntimeOnly();
                DependencyCollector getCompileOnly();
            }
        """

        def javaSourceDir = new File(jvmPluginDir, 'src/main/java/org/gradle/api/experimental/java')
        javaSourceDir.mkdirs()

        file javaSourceDir, 'JavaApplication.java', """
            package org.gradle.api.experimental.java;

            import org.gradle.api.experimental.jvm.HasJavaTarget;
            import org.gradle.api.experimental.jvm.HasJvmApplication;
            import org.gradle.declarative.dsl.model.annotations.Restricted;

            /**
             * An application implemented using a single version of Java.
             */
            @Restricted
            public interface JavaApplication extends HasJavaTarget, HasJvmApplication {
            }
        """

        file javaSourceDir, 'StandaloneJavaApplicationPlugin.java', """
            package org.gradle.api.experimental.java;

            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.internal.plugins.software.SoftwareType;
            import org.gradle.api.plugins.ApplicationPlugin;

            /**
             * Creates a declarative {@link JavaApplication} DSL model, applies the official Java application plugin,
             * and links the declarative model to the official plugin.
             */
            abstract public class StandaloneJavaApplicationPlugin implements Plugin<Project> {
                @SoftwareType(name = "javaApplication", modelPublicType = JavaApplication.class)
                abstract public JavaApplication getApplication();

                @Override
                public void apply(Project project) {
                    JavaApplication dslModel = getApplication();

                    project.getPlugins().apply(ApplicationPlugin.class);

                    linkDslModelToPlugin(project, dslModel);
                }

                private void linkDslModelToPlugin(Project project, JavaApplication dslModel) {
                    // not done for the purposes of this perf test
                }
            }
        """
    }

    static void main(String[] args) {
        def projectName = args[0]
        def outputDir = new File(args[1])

        JavaTestProjectGenerator project = JavaTestProjectGenerator.values().find {it.projectName == projectName }
        if (project == null) {
            throw new IllegalArgumentException("Project not defined: $projectName")
        }
        def projectDir = new File(outputDir, projectName)
        new DeclarativeDslTestProjectGenerator(project.config).generate(outputDir)

        println "Generated: $projectDir"
    }

}
