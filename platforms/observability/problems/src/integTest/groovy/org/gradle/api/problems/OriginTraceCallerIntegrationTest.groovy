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
import org.gradle.api.problems.internal.location.FilePosition
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.problems.ReceivedProblem
import org.gradle.internal.code.Locality
import org.gradle.test.fixtures.file.TestFile

/**
 * The origin leaf of a problem reported with {@code callerLocation(api)}: the first stack frame outside Gradle and outside the API's package,
 * whether that is a build script or compiled code.
 */
class OriginTraceCallerIntegrationTest extends AbstractIntegrationSpec {

    private static final String PROBLEM_ID = "problem from spawned thread"

    def setup() {
        enableProblemsApiCheck()
        file("buildSrc/build.gradle") << """
            plugins {
                id "java-gradle-plugin"
            }
            gradlePlugin {
                plugins {
                    acme {
                        id = "com.acme.foo"
                        implementationClass = "com.acme.api.AcmePlugin"
                    }
                }
            }
        """
        file("buildSrc/src/main/java/com/acme/api/AcmePlugin.java") << """
            package com.acme.api;

            import org.gradle.api.Plugin;
            import org.gradle.api.Project;

            public class AcmePlugin implements Plugin<Project> {
                public void apply(Project project) {
                    project.getExtensions().create("acme", AcmeExtension.class);
                }
            }
        """
        file("buildSrc/src/main/java/com/acme/api/AcmeExtension.java") << """
            package com.acme.api;

            import org.gradle.api.problems.Problems;
            import javax.inject.Inject;

            public abstract class AcmeExtension {
                @Inject
                protected abstract Problems getProblems();

                /** The API: whoever calls this is at fault. */
                public void check() {
                    getProblems().getReporter().report(getProblems().getGroups().getOthers().group("Generic").problemId("$PROBLEM_ID"), spec -> {
                        spec.callerLocation(AcmeExtension.class);
                    });
                }
            }
        """
        file("buildSrc/src/main/java/com/acme/util/Helper.java") << """
            package com.acme.util;

            import com.acme.api.AcmeExtension;

            public class Helper {
                public static void callCheck(AcmeExtension acme) {
                    acme.check();
                }
            }
        """
    }

    def "the caller in a build script is the origin leaf, as a local script line"() {
        given:
        buildFile """
            plugins {
                id "com.acme.foo"
            }
            acme.check()
        """

        when:
        run("help")

        then:
        def leaf = problem().originTraces[0].frames[0]
        leaf.displayName == buildFile.absolutePath + ":5"
        leaf.locality == Locality.LOCAL
        (leaf.position as FilePosition).line == 5
    }

    def "the caller in a plugin's compiled code is the origin leaf, with the file and line from its debug information"() {
        given:
        consumerPlugin """
            project.getPluginManager().apply("com.acme.foo");
            com.acme.util.Helper.callCheck((com.acme.api.AcmeExtension) project.getExtensions().getByName("acme"));
        """
        buildFile """
            plugins {
                id "com.acme.consumer"
            }
        """

        when:
        run("help")

        then:
        def leaf = problem().originTraces[0].frames[0]
        leaf.displayName == "Helper.java:8"
        with(leaf.position as CompiledCodePosition) {
            fileName == "Helper.java"
            className == "com.acme.util.Helper"
            line == 8
            module == null
        }

        and: "a helper in the plugin's own jar stays the leaf, and takes the plugin's locality"
        leaf.locality == Locality.LOCAL
    }

    def "a compiled caller that a build script called gives way to the script line"() {
        given:
        buildFile """
            plugins {
                id "com.acme.foo"
            }
            com.acme.util.Helper.callCheck(acme)
        """

        when:
        run("help")

        then:
        def problem = problem()
        def leaf = problem.originTraces[0].frames[0]
        leaf.displayName == buildFile.absolutePath + ":5"
        leaf.locality == Locality.LOCAL

        and: "the flat list, which older clients read, points at the same line"
        (problem.oneLocation(StackTraceLocation).fileLocation as LineInFileLocation).line == 5
    }

    def "a library line between the plugin and the API gives way to the plugin's own line"() {
        given:
        libraryThatCallsTheApi()
        def plugin = consumerPlugin """
            project.getPluginManager().apply("com.acme.foo");
            com.thirdparty.Relay.callCheck(project.getExtensions().getByName("acme"));
        """
        buildFile """
            plugins {
                id "com.acme.consumer"
            }
        """

        when:
        run("help")

        then:
        def leaf = problem().originTraces[0].frames[0]
        leaf.displayName == "ConsumerPlugin.java:" + lineOf(plugin, "Relay.callCheck")
        (leaf.position as CompiledCodePosition).className == "com.acme.consumer.ConsumerPlugin"

        and: "the leaf takes the locality of the plugin whose jar holds it"
        leaf.locality == Locality.LOCAL
    }

    def "a library line in a task action gives way to the line of the plugin that registered the task, also on a configuration cache hit"() {
        given:
        libraryThatCallsTheApi()
        def plugin = consumerPlugin """
            project.getPluginManager().apply("com.acme.foo");
            Object acme = project.getExtensions().getByName("acme");
            project.getTasks().register("relay", task -> task.doLast(t -> com.thirdparty.Relay.callCheck(acme)));
        """
        buildFile """
            plugins {
                id "com.acme.consumer"
            }
        """

        when:
        executer.withArgument("--configuration-cache")
        run("relay")

        then:
        problem().originTraces[0].frames[0].displayName == "ConsumerPlugin.java:" + lineOf(plugin, "Relay.callCheck")

        when:
        executer.withArgument("--configuration-cache")
        run("relay")

        then:
        def leaf = problem().originTraces[0].frames[0]
        leaf.displayName == "ConsumerPlugin.java:" + lineOf(plugin, "Relay.callCheck")
        leaf.locality == Locality.LOCAL
    }

    def "a script between the library and the plugin becomes the leaf, not the plugin that applied the script"() {
        given:
        libraryThatCallsTheApi()
        consumerPlugin """
            project.getPluginManager().apply("com.acme.foo");
            project.apply(java.util.Collections.singletonMap("from", "gradle/shared.gradle"));
        """
        file("gradle/shared.gradle") << """
            com.thirdparty.Relay.callCheck(acme)
        """
        buildFile """
            plugins {
                id "com.acme.consumer"
            }
        """

        when:
        run("help")

        then:
        def leaf = problem().originTraces[0].frames[0]
        leaf.displayName == file("gradle/shared.gradle").absolutePath + ":2"
        leaf.locality == Locality.LOCAL
    }

    /**
     * A library in a jar of its own, which calls the API for whoever calls the library.
     */
    private void libraryThatCallsTheApi() {
        file("buildSrc/settings.gradle") << """
            include "relay"
        """
        file("buildSrc/relay/build.gradle") << """
            plugins {
                id "java-library"
            }
        """
        file("buildSrc/build.gradle") << """
            dependencies {
                implementation project(":relay")
            }
        """
        file("buildSrc/relay/src/main/java/com/thirdparty/Relay.java") << """
            package com.thirdparty;

            public class Relay {
                public static void callCheck(Object acme) {
                    try {
                        acme.getClass().getMethod("check").invoke(acme);
                    } catch (ReflectiveOperationException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        """
    }

    private TestFile consumerPlugin(String applyBody) {
        file("buildSrc/build.gradle") << """
            gradlePlugin {
                plugins {
                    consumer {
                        id = "com.acme.consumer"
                        implementationClass = "com.acme.consumer.ConsumerPlugin"
                    }
                }
            }
        """
        file("buildSrc/src/main/java/com/acme/consumer/ConsumerPlugin.java") << """
            package com.acme.consumer;

            import org.gradle.api.Plugin;
            import org.gradle.api.Project;

            public class ConsumerPlugin implements Plugin<Project> {
                public void apply(Project project) {
                    $applyBody
                }
            }
        """
    }

    private static int lineOf(File source, String text) {
        source.readLines().findIndexOf { it.contains(text) } + 1
    }

    private ReceivedProblem problem() {
        findReceivedProblem { it.fqid == "Others:Generic:$PROBLEM_ID" }
    }
}
