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

package org.gradle.problems.internal.services

import org.gradle.api.problems.ProblemGroup
import org.gradle.api.problems.ProblemId
import org.gradle.api.problems.internal.GradleCoreProblemGroup
import org.gradle.api.problems.internal.ProblemsInternal
import org.gradle.util.TestUtil
import spock.lang.Shared
import spock.lang.Specification

class LegacyProblemIdentityNaggerTest extends Specification {

    @Shared
    ProblemsInternal problems = TestUtil.problemsService()

    def "ids from the predefined hierarchy need no nag"() {
        expect:
        LegacyProblemIdentityNagger.legacyGroupToReplace(id) == null

        where:
        id << [
            problems.groups.compilation.java.problemId("Unused import"),
            problems.groups.others.group("Generic").problemId("label"),
            problems.groups.transformation.group("KMP").group("JavaScript").problemId("Bundle failed"),
            problems.groups.others.undefined.problemId("Something odd"),
        ]
    }

    def "ids whose chain was created through ProblemGroup.create() name the outermost legacy group"() {
        expect:
        LegacyProblemIdentityNagger.legacyGroupToReplace(id).name == groupToReplace

        where:
        id                                                                                                              | groupToReplace
        ProblemId.create("type", "label", ProblemGroup.create("generic", "Generic"))                                   | "generic"
        ProblemId.create("type", "label", ProblemGroup.create("child", "Child", ProblemGroup.create("root", "Root"))) | "root"
        ProblemId.create("type", "label", ProblemGroup.create("Lint", "Lint", problems.groups.compilation.java))      | "Lint"
    }

    def "Gradle's own legacy groups are exempt until stage 2 migrates them"() {
        expect:
        LegacyProblemIdentityNagger.legacyGroupToReplace(id) == null

        where:
        id << [
            ProblemId.create("no-matches", "No matches", GradleCoreProblemGroup.taskSelection()),
            ProblemId.create("missing-annotation", "Missing annotation", GradleCoreProblemGroup.validation().property()),
            ProblemId.create("feature", "Feature", GradleCoreProblemGroup.deprecation()),
            // children Gradle creates below its own roots, like validation > configuration-cache
            ProblemId.create("cc", "CC", ProblemGroup.create("configuration-cache", "configuration cache validation", GradleCoreProblemGroup.validation().thisGroup())),
            // roots created outside GradleCoreProblemGroup
            ProblemId.create("invalid-jvm-installation", "Invalid JVM installation", ProblemGroup.create("jvm-toolchain", "JVM Toolchain")),
            ProblemId.create("missing-id", "Problem id must be specified", ProblemGroup.create("problems-api", "Problems API")),
        ]
    }

    def "a third-party root is not mistaken for a Gradle-owned one"() {
        expect:
        LegacyProblemIdentityNagger.legacyGroupToReplace(ProblemId.create("type", "label", ProblemGroup.create("kotlin", "Kotlin"))).name == "kotlin"
    }
}
