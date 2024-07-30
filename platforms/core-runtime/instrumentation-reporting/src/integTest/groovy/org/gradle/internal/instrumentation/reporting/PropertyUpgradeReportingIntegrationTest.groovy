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

package org.gradle.internal.instrumentation.reporting

import groovy.test.NotYetImplemented
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.util.internal.ToBeImplemented

class PropertyUpgradeReportingIntegrationTest extends AbstractIntegrationSpec {

    def "usage of upgraded properties in buildSrc should be reported"() {
        given:
        executer.requireOwnGradleUserHomeDir("We cache report in global cache")
        javaFile("buildSrc/src/main/java/test/MyPlugin.java", """
            package test;

            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.tasks.compile.JavaCompile;

            public abstract class MyPlugin implements Plugin<Project> {
                @Override
                public void apply(Project project) {
                    project.getTasks().register("myJavaCompile", JavaCompile.class, task -> {
                        task.getSource();
                        task.setSource(project.files());
                    });
                }
            }
        """)

        when:
        run("--property-upgrade-report")

        then:
        postBuildOutputContains("Intercepted methods:")
        postBuildOutputContains("org.gradle.api.tasks.compile.JavaCompile.getSource(): at test.MyPlugin(MyPlugin.java:12)")
        postBuildOutputContains("org.gradle.api.tasks.compile.JavaCompile.setSource(): at test.MyPlugin(MyPlugin.java:13)")
    }

    def "usage of upgraded properties in Kotlin scripts should be reported"() {
        given:
        executer.requireOwnGradleUserHomeDir("We cache report in global cache")
        buildKotlinFile << """
            plugins {
                id("java-library")
            }

            tasks.register<JavaCompile>("myJavaCompile") {
                source
                source = project.files().asFileTree
            }
        """

        when:
        run("--property-upgrade-report")

        then:
        postBuildOutputContains("Intercepted methods:")
        postBuildOutputContains("org.gradle.api.tasks.compile.JavaCompile.getSource(): at build.gradle(file://${buildKotlinFile.absolutePath}:7)")
        postBuildOutputContains("org.gradle.api.tasks.compile.JavaCompile.setSource(): at build.gradle(file://${buildKotlinFile.absolutePath}:8)")
    }

    def "usage of upgraded properties is reported even when report comes from cache"() {
        given:
        javaFile("buildSrc/src/main/java/test/MyPlugin.java", """
            package test;

            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.tasks.compile.JavaCompile;

            public abstract class MyPlugin implements Plugin<Project> {
                @Override
                public void apply(Project project) {
                    project.getTasks().register("myJavaCompile", JavaCompile.class, task -> {
                        task.getSource();
                        task.setSource(project.files());
                    });
                }
            }
        """)
        buildKotlinFile << """
            plugins {
                id("java-library")
            }

            tasks.register<JavaCompile>("myJavaCompile") {
                source
                source = project.files().asFileTree
            }
        """

        when:
        // Run twice so the second report is from cache
        run("--property-upgrade-report")
        run("--property-upgrade-report")

        then:
        postBuildOutputContains("Intercepted methods:")
        postBuildOutputContains("org.gradle.api.tasks.compile.JavaCompile.getSource(): at test.MyPlugin(MyPlugin.java:12)")
        postBuildOutputContains("org.gradle.api.tasks.compile.JavaCompile.setSource(): at test.MyPlugin(MyPlugin.java:13)")
        postBuildOutputContains("org.gradle.api.tasks.compile.JavaCompile.getSource(): at build.gradle(file://${buildKotlinFile.absolutePath}:7)")
        postBuildOutputContains("org.gradle.api.tasks.compile.JavaCompile.setSource(): at build.gradle(file://${buildKotlinFile.absolutePath}:8)")
    }

    def "should not report upgraded properties if --property-upgrade-report flag is not used"() {
        given:
        executer.requireOwnGradleUserHomeDir("We cache report in global cache")
        javaFile("buildSrc/src/main/java/test/MyPlugin.java", """
            package test;

            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.tasks.compile.JavaCompile;

            public abstract class MyPlugin implements Plugin<Project> {
                @Override
                public void apply(Project project) {
                    project.getTasks().register("myJavaCompile", JavaCompile.class, task -> {
                        task.getSource();
                        task.setSource(project.files());
                    });
                }
            }
        """)
        buildKotlinFile << """
            plugins {
                id("java-library")
            }

            tasks.register<JavaCompile>("myJavaCompile") {
                source
                source = project.files().asFileTree
            }
        """

        when:
        run("help")

        then:
        postBuildOutputDoesNotContain("Intercepted methods:")
        postBuildOutputDoesNotContain("at test.MyPlugin")
        postBuildOutputDoesNotContain("at build.gradle")
    }

    @NotYetImplemented
    @ToBeImplemented("Inherited properties are not reported for project dependency classes")
    def "usage of upgraded properties in extended class should be reported"() {
        given:
        executer.requireOwnGradleUserHomeDir("Run with empty cache, so report is always generated")
        javaFile("buildSrc/src/main/java/MyJavaCompile.java", """
            import org.gradle.api.tasks.compile.JavaCompile;

            public abstract class MyJavaCompile extends JavaCompile {
            }
        """)
        javaFile("buildSrc/src/main/java/MyPlugin.java", """
            import org.gradle.api.Plugin;
            import org.gradle.api.Project;

            public abstract class MyPlugin implements Plugin<Project> {
                @Override
                public void apply(Project project) {
                    project.getTasks().register("myJavaCompile", MyJavaCompile.class, task -> {
                        task.getSource();
                        task.setSource(project.files());
                    });
                }
            }
        """)
        buildFile << """
            apply plugin: MyPlugin
        """

        when:
        run("help", "--property-upgrade-report")

        then:
        postBuildOutputContains("Intercepted method: MyPlugin.class: org/gradle/api/tasks/compile/JavaCompile#getSource()Lorg/gradle/api/file/FileTree;")
    }
}
