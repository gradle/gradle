/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.integtests

import org.gradle.api.DefaultTask
import org.gradle.api.internal.ConventionTask
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.plugins.quality.CodeNarc
import org.gradle.api.plugins.quality.Pmd
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.bundling.Tar
import org.gradle.api.tasks.bundling.War
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.compile.GroovyCompile
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.scala.ScalaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.integtests.fixtures.CrossVersionIntegrationSpec
import org.gradle.jvm.application.tasks.CreateStartScripts
import org.gradle.jvm.tasks.Jar
import org.gradle.plugins.ear.Ear
import org.gradle.plugins.ide.eclipse.GenerateEclipseClasspath
import org.gradle.plugins.ide.eclipse.GenerateEclipseJdt
import org.gradle.plugins.ide.eclipse.GenerateEclipseProject
import org.gradle.plugins.ide.eclipse.GenerateEclipseWtpComponent
import org.gradle.plugins.ide.eclipse.GenerateEclipseWtpFacet
import org.gradle.plugins.ide.idea.GenerateIdeaModule
import org.gradle.plugins.ide.idea.GenerateIdeaProject
import org.gradle.plugins.ide.idea.GenerateIdeaWorkspace
import org.gradle.plugins.signing.Sign
import org.gradle.util.GradleVersion

/**
 * Tests that task classes compiled against one version of Gradle are compatible with another version.
 */
abstract class AbstractTaskSubclassingBinaryCompatibilityCrossVersionSpec extends CrossVersionIntegrationSpec {
    protected void prepareSubclassingTest(GradleVersion targetVersion) {
        def taskClasses = [
            DefaultTask,
            SourceTask,
            ConventionTask,
            Copy,
            Sync,
            Zip,
            org.gradle.api.tasks.bundling.Jar,
            Tar,
            War,
            JavaCompile,
            GroovyCompile,
            ScalaCompile,
            Test,
            CodeNarc,
            Checkstyle,
            Ear,
            Pmd,
            Sign,
            org.gradle.api.tasks.application.CreateStartScripts,
            GenerateEclipseJdt,
            GenerateEclipseClasspath,
            GenerateEclipseProject,
            GenerateEclipseWtpComponent,
            GenerateEclipseWtpFacet,
            GenerateIdeaModule,
            GenerateIdeaWorkspace,
            GenerateIdeaProject
        ]

        // Task types added after 1.0

        if (targetVersion >= GradleVersion.version("2.4")) {
            taskClasses += CreateStartScripts
        }
        if (targetVersion >= GradleVersion.version("2.3")) {
            taskClasses += Jar
        }

        // Some breakages that were not detected prior to release. Please do not add any more exceptions

        if (targetVersion < GradleVersion.version("1.1")) {
            // Breaking changes were made to Test between 1.0 and 1.1
            taskClasses.remove(Test)
        }
        if (targetVersion < GradleVersion.version("2.0")) {
            // Breaking changes were made to JavaCompile prior to 2.0
            taskClasses.remove(JavaCompile)
        }

        Map<String, String> subclasses = taskClasses.collectEntries { ["custom" + it.name.replace(".", "_"), it.name] }
        def apiDepConf = "implementation"
        if (targetVersion < GradleVersion.version("7.0-rc-1")) {
            apiDepConf = "compile"
        }
        def groovyDepConf
        if (targetVersion < GradleVersion.version("1.4-rc-1")) {
            groovyDepConf = "groovy"
        } else {
            groovyDepConf = apiDepConf
        }
        file("producer/build.gradle") << """
            apply plugin: 'groovy'
            dependencies {
                ${groovyDepConf} localGroovy()
                ${apiDepConf} gradleApi()
            }
        """

        file("producer/src/main/groovy/SomePlugin.groovy") << """
            import org.gradle.api.Plugin
            import org.gradle.api.Project

            class SomePlugin implements Plugin<Project> {
                void apply(Project p) { """ <<      \
                 subclasses.collect { "p.tasks.create('${it.key}', ${it.key})" }.join("\n") << """
                }
            }
            """ <<      \
                 subclasses.collect {
            def className = it.key
            """${declareTaskClass(targetVersion)} class ${className} extends ${it.value} {
    ${className}() {
        // GRADLE-3185
        project.logger.lifecycle('task created')
        // GRADLE-3207
        super.getServices()
    }
}"""
        }.join("\n")

        buildFile << """
buildscript {
    dependencies { classpath fileTree(dir: "producer/build/libs", include: '*.jar') }
}

apply plugin: SomePlugin
"""
    }

    protected void prepareMethodUseTest(GradleVersion targetVersion) {
        file("someFile").touch()
        file("anotherFile").touch()
        file("yetAnotherFile").touch()
        file("someDir").createDir()

        def apiDepConf = "implementation"
        if (targetVersion < GradleVersion.version("7.0-rc-1")) {
            apiDepConf = "compile"
        }
        def groovyDepConf
        if (targetVersion < GradleVersion.version("1.4-rc-1")) {
            groovyDepConf = "groovy"
        } else {
            groovyDepConf = apiDepConf
        }
        file("producer/build.gradle") << """
            apply plugin: 'groovy'
            dependencies {
                ${groovyDepConf} localGroovy()
                ${apiDepConf} gradleApi()
            }
        """

        boolean previousVersionLeaksInternal = (targetVersion == GradleVersion.version("3.2") ||
            targetVersion == GradleVersion.version("3.2.1"))

        file("producer/src/main/java/SubclassTask.java") << """
            import org.gradle.api.DefaultTask;
            import org.gradle.api.tasks.*;
            import org.gradle.api.logging.LogLevel;
            import java.util.*;

            public ${declareTaskClass(targetVersion)} class SubclassTask extends DefaultTask {
                public SubclassTask() {
                    ${previousVersionLeaksInternal ? "((TaskInputs)getInputs())" : "getInputs()"}.file("someFile");
                    ${previousVersionLeaksInternal ? "((TaskInputs)getInputs())" : "getInputs()"}.files("anotherFile", "yetAnotherFile");
                    ${previousVersionLeaksInternal ? "((TaskInputs)getInputs())" : "getInputs()"}.dir("someDir");
                    ${targetVersion >= GradleVersion.version("4.3")
            ? 'getInputs().property("input", "value");'
            : ""}
                    Map<String, Object> mapValues = new HashMap<String, Object>();
                    mapValues.put("mapInput", "mapValue");
                    ${previousVersionLeaksInternal ? "((TaskInputs)getInputs())" : "getInputs()"}.properties(mapValues);
                }

                @TaskAction
                public void doGet() {
                    // Note: not all of these specialise at time of writing, but may do in the future
                    getTaskDependencies();
                    getState();
                    getLogging();
                    getLogging().captureStandardOutput(LogLevel.INFO);
                    getStandardOutputCapture();
                    getInputs();
                    getOutputs();
                }
            }
        """

        buildFile << """
            buildscript {
                dependencies { classpath fileTree(dir: "producer/build/libs", include: '*.jar') }
            }

            task t(type: SubclassTask)
        """
    }

    protected static String declareTaskClass(GradleVersion targetVersion) {
        if (targetVersion >= GradleVersion.version("7.0")) {
            return "abstract"
        } else {
            return ""
        }
    }
}
