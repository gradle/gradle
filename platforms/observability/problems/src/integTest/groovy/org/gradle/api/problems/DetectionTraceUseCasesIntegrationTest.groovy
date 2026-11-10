/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.problems

import org.gradle.api.problems.internal.StackTraceLocation
import org.gradle.api.problems.internal.location.CompiledCodePosition
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.configurationcache.ConfigurationCacheFixture
import org.gradle.integtests.fixtures.problems.ReceivedProblem
import org.gradle.internal.code.Locality

/**
 * The detection traces of the use cases in the "Problem Locations Revised" spec. Each test is named after its use case.
 * Only detection is covered; the origin traces of the spec don't exist yet.
 */
class DetectionTraceUseCasesIntegrationTest extends AbstractIntegrationSpec {

    private static final String NAG = """
        org.gradle.internal.deprecation.DeprecationLogger
            .deprecate("Feature")
            .willBeRemovedInGradle10()
            .undocumented()
            .nagUser()
    """

    def setup() {
        enableProblemsApiCheck()
    }

    def "UC2: validation problem of a task type is detected in the task"() {
        given:
        settingsFile """
            include "app"
        """
        file("buildSrc/src/main/java/com/acme/AcmeTask.java") << """
            package com.acme;

            import org.gradle.api.DefaultTask;
            import org.gradle.api.tasks.TaskAction;

            public class AcmeTask extends DefaultTask {
                private String bar;

                public String getBar() { return bar; }
                public void setBar(String bar) { this.bar = bar; }

                @TaskAction
                public void run() {}
            }
        """
        file("app/build.gradle") << """
            tasks.register("acme", com.acme.AcmeTask)
        """

        when:
        fails(":app:acme")

        then:
        detectionOf("Gradle:Plugin Validation:Missing annotation") == ["task :app:acme", "project :app", "build :"]

        and: "the origin is the property and its type"
        originsOf(problem) == [["property bar", "class com.acme.AcmeTask"]]
    }

    def "UC3: deprecation called from a build script in a task action is detected in the task"() {
        given:
        settingsFile """
            include "app"
        """
        file("app/build.gradle") << """
            tasks.register("test") {
                doLast {
                    $NAG
                }
            }
        """
        expectFeatureDeprecation()

        when:
        run(":app:test")

        then:
        detectionOf("Gradle:Deprecation:Feature") == ["task :app:test", "project :app", "build :"]

        and: "the origin is the script line, local, as the flat list says too"
        originsOf(problem) == [[file("app/build.gradle").absolutePath + ":6"]]
        problem.originTraces[0].frames[0].locality == Locality.LOCAL
        problem.oneLocation(StackTraceLocation).fileLocation.path == file("app/build.gradle").absolutePath
    }

    def "UC4: deprecation called from a compiled plugin while it is applied is detected in the plugin application"() {
        given:
        settingsFile """
            include "app"
        """
        pluginInBuildSrc("com.acme.foo", "com.acme.AcmePlugin", """
            org.gradle.internal.deprecation.DeprecationLogger
                .deprecate("Feature")
                .willBeRemovedInGradle10()
                .undocumented()
                .nagUser();
        """)
        file("app/build.gradle") << """
            plugins {
                id "com.acme.foo"
            }
        """
        expectFeatureDeprecation()

        when:
        run(":app:help")

        then:
        detectionOf("Gradle:Deprecation:Feature") == ["plugin application com.acme.foo", "project :app", "build :"]

        and: "the origin is the line in the compiled plugin, from its debug information (UC4, #26891)"
        def leaf = problem.originTraces[0].frames[0]
        leaf.displayName == "AcmePlugin.java:" + nagLineOf(file("buildSrc/src/main/java/com/acme/AcmePlugin.java"))
        (leaf.position as CompiledCodePosition).className == "com.acme.AcmePlugin"
        problem.oneLocation(StackTraceLocation).fileLocation == null
    }

    def "UC6: problem reported from an unmanaged thread is detected in the build"() {
        given:
        settingsFile """
            include "app"
        """
        pluginInBuildSrc("com.acme.foo", "com.acme.AcmePlugin", """
            Thread thread = new Thread(() -> getProblems().getReporter().report(
                getProblems().getGroups().getOthers().group("Generic").problemId("problem from spawned thread"), spec -> {}));
            thread.start();
            try {
                thread.join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        """)
        file("app/build.gradle") << """
            plugins {
                id "com.acme.foo"
            }
        """

        when:
        run(":app:help")

        then:
        detectionOf("Others:Generic:problem from spawned thread") == ["build :"]
    }

    def "UC7: Java compilation error from the compiler worker is detected in the compile task"() {
        given:
        settingsFile """
            include "app"
        """
        file("app/build.gradle") << """
            plugins {
                id "java"
            }
        """
        file("app/src/main/java/Foo.java") << """
            public class Foo {
                void broken() {
                    int x = ;
                }
            }
        """

        when:
        fails(":app:compileJava")

        then:
        def problem = findReceivedProblem { it.fqid.startsWith("Compilation:Java:") }
        problem.detectionTrace.frames*.displayName == ["task :app:compileJava", "project :app", "build :"]

        and: "the origin is the line of the Java file, local"
        originsOf(problem) == [[file("app/src/main/java/Foo.java").absolutePath + ":4:29"]]
        problem.originTraces[0].frames[0].locality == Locality.LOCAL
    }

    def "UC8: deprecation called from a settings script is detected in the build"() {
        given:
        settingsFile """
            $NAG
        """
        expectFeatureDeprecation()

        when:
        run("help")

        then:
        detectionOf("Gradle:Deprecation:Feature") == ["build :"]
        originsOf(problem) == [[settingsFile.absolutePath + ":4"]]
        problem.originTraces[0].frames[0].locality == Locality.LOCAL
    }

    def "UC9: deprecation in a build script of an included build is detected in that build's project"() {
        given:
        settingsFile """
            includeBuild "lib"
        """
        file("lib/settings.gradle") << """
            rootProject.name = "lib"
            include "core"
        """
        file("lib/core/build.gradle") << """
            $NAG
        """
        buildFile """
            tasks.register("useLib") {
                dependsOn gradle.includedBuild("lib").task(":core:help")
            }
        """
        expectFeatureDeprecation()

        when:
        run("useLib")

        then:
        detectionOf("Gradle:Deprecation:Feature") == ["project :lib:core", "build :lib", "build :"]
        originsOf(problem) == [[file("lib/core/build.gradle").absolutePath + ":4"]]
        problem.originTraces[0].frames[0].locality == Locality.LOCAL
    }

    def "UC10: deprecation in a task action on a configuration cache hit is detected in the task"() {
        given:
        settingsFile """
            include "app"
        """
        file("app/build.gradle") << """
            tasks.register("test") {
                doLast {
                    $NAG
                }
            }
        """
        def configurationCache = new ConfigurationCacheFixture(this)
        expectFeatureDeprecation()
        run(":app:test", "--configuration-cache")

        when:
        expectFeatureDeprecation()
        run(":app:test", "--configuration-cache")

        then:
        configurationCache.assertStateLoaded()
        detectionOf("Gradle:Deprecation:Feature") == ["task :app:test", "project :app", "build :"]
    }

    def "UC11: plugin type problem found by validatePlugins is detected in that task"() {
        given:
        settingsFile """
            include "plugin"
        """
        file("plugin/build.gradle") << """
            plugins {
                id "java-gradle-plugin"
            }
            gradlePlugin {
                plugins {
                    acme {
                        id = "com.acme.foo"
                        implementationClass = "com.acme.AcmePlugin"
                    }
                }
            }
        """
        file("plugin/src/main/java/com/acme/AcmePlugin.java") << """
            package com.acme;

            import org.gradle.api.Plugin;
            import org.gradle.api.Project;

            public class AcmePlugin implements Plugin<Project> {
                public void apply(Project project) {}
            }
        """
        file("plugin/src/main/java/com/acme/AcmeTask.java") << """
            package com.acme;

            import org.gradle.api.DefaultTask;
            import org.gradle.api.tasks.TaskAction;

            public class AcmeTask extends DefaultTask {
                private String bar;

                public String getBar() { return bar; }
                public void setBar(String bar) { this.bar = bar; }

                @TaskAction
                public void run() {}
            }
        """

        when:
        fails(":plugin:validatePlugins")

        then:
        detectionOf("Gradle:Plugin Validation:Missing annotation") == ["task :plugin:validatePlugins", "project :plugin", "build :"]
        originsOf(problem) == [["property bar", "class com.acme.AcmeTask"]]
    }

    private void pluginInBuildSrc(String id, String implementationClass, String applyBody) {
        def packageName = implementationClass.substring(0, implementationClass.lastIndexOf('.'))
        def className = implementationClass.substring(implementationClass.lastIndexOf('.') + 1)
        file("buildSrc/build.gradle") << """
            plugins {
                id "java-gradle-plugin"
            }
            gradlePlugin {
                plugins {
                    acme {
                        id = "$id"
                        implementationClass = "$implementationClass"
                    }
                }
            }
        """
        file("buildSrc/src/main/java/${packageName.replace('.', '/')}/${className}.java") << """
            package $packageName;

            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.problems.Problems;
            import javax.inject.Inject;

            public abstract class $className implements Plugin<Project> {
                @Inject
                protected abstract Problems getProblems();

                @Override
                public void apply(Project project) {
                    $applyBody
                }
            }
        """
    }

    private void expectFeatureDeprecation() {
        executer.expectDocumentedDeprecationWarning("Feature has been deprecated. This is scheduled to be removed in Gradle 10.")
    }

    private ReceivedProblem problem

    private List<String> detectionOf(String fqid) {
        problem = findReceivedProblem { it.fqid == fqid }
        problem.detectionTrace.frames*.displayName
    }

    /** The line javac attributes the deprecation call to: the line of the method that nags, in a chained call. */
    private static int nagLineOf(File source) {
        source.readLines().findIndexOf { it.contains(".nagUser()") } + 1
    }

    private static List<List<String>> originsOf(ReceivedProblem problem) {
        problem.originTraces*.frames*.displayName
    }
}
