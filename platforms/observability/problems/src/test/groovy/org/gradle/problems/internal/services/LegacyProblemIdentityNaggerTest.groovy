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
import org.gradle.api.problems.internal.ProblemsInternal
import org.gradle.util.TestUtil
import spock.lang.Shared
import spock.lang.Specification

class LegacyProblemIdentityNaggerTest extends Specification {

    @Shared
    ProblemsInternal problems = TestUtil.problemsService()

    def "ids from the predefined hierarchy need no nag"() {
        expect:
        !LegacyProblemIdentityNagger.isLegacy(id)

        where:
        id << [
            problems.groups.compilation.java.problemId("Unused import"),
            problems.groups.others.group("Generic").problemId("label"),
            problems.groups.transformation.group("KMP").group("JavaScript").problemId("Bundle failed"),
            problems.groups.others.undefined.problemId("Something odd"),
        ]
    }

    def "ids created through ProblemId.create() or with a group created through ProblemGroup.create() need a nag"() {
        expect:
        LegacyProblemIdentityNagger.isLegacy(id)

        where:
        id << [
            ProblemId.create("type", "label", ProblemGroup.create("generic", "Generic")),
            ProblemId.create("type", "label", ProblemGroup.create("child", "Child", ProblemGroup.create("root", "Root"))),
            ProblemId.create("type", "label", ProblemGroup.create("Lint", "Lint", problems.groups.compilation.java)),
            // matches a predefined group, which the reporter replaces before the problem reaches the nag
            ProblemId.create("Unused import", "Unused import", ProblemGroup.create("Java", "Java", ProblemGroup.create("Compilation", "Compilation"))),
            // only the id comes from the factory
            ProblemId.create("Unused import", "Unused import", problems.groups.compilation.java),
        ]
    }

    def "the advice is based on the id after the reporter replaced factory groups: #description"() {
        expect:
        LegacyProblemIdentityNagger.advice(id) == advice

        where:
        description                                    | id                                                                                                             | advice
        "a legacy root"                                | ProblemId.create("type", "label", ProblemGroup.create("child", "Child", ProblemGroup.create("root", "Root"))) | 'Create the group from the predefined hierarchy instead, for example problems.getGroups().getOthers().group("Root").'
        "a legacy root with an invalid display name"   | ProblemId.create("type", "label", ProblemGroup.create("root", "org.example:root"))                            | 'Create the group from the predefined hierarchy instead, for example problems.getGroups().getOthers().group(name).'
        "a legacy group below a predefined group"      | ProblemId.create("type", "label", ProblemGroup.create("lint", "Lint", problems.groups.compilation.java))      | 'Create the group below the predefined group \'Compilation > Java\' from problems.getGroups() instead, with group("Lint").'
        "a legacy group below a closed group"          | ProblemId.create("type", "label", ProblemGroup.create("caching", "Caching", problems.groups.gradle.pluginValidation)) | 'Create the group from the predefined hierarchy instead, for example problems.getGroups().getOthers().group(name).'
        "every factory group matched a predefined one" | problems.groups.compilation.java.problemId("Unused import")                                                    | 'Create the id with problemId() on the predefined group \'Compilation > Java\' from problems.getGroups() instead.'
    }
}
