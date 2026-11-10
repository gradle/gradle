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

    def "the caller in compiled code is the origin leaf, with the file and line from its debug information"() {
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
        leaf.displayName == "Helper.java:8"
        with(leaf.position as CompiledCodePosition) {
            fileName == "Helper.java"
            className == "com.acme.util.Helper"
            line == 8
            module == null
        }

        and: "the flat list, which older clients read, still points at the script line Gradle located below the compiled caller, as before"
        (problem.oneLocation(StackTraceLocation).fileLocation as LineInFileLocation).line == 5
    }

    private ReceivedProblem problem() {
        findReceivedProblem { it.fqid == "Others:Generic:$PROBLEM_ID" }
    }
}
