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

package org.gradle.api.internal.catalog.problems

import org.gradle.util.TestUtil
import spock.lang.Specification

import static org.gradle.api.internal.catalog.problems.ResolutionFailureProblemId.AMBIGUOUS_ARTIFACTS
import static org.gradle.api.internal.catalog.problems.ResolutionFailureProblemId.AMBIGUOUS_ARTIFACT_TRANSFORM
import static org.gradle.api.internal.catalog.problems.ResolutionFailureProblemId.NO_COMPATIBLE_ARTIFACT
import static org.gradle.api.internal.catalog.problems.ResolutionFailureProblemId.UNKNOWN_ARTIFACT_SELECTION_FAILURE

class ResolutionFailureProblemIdTest extends Specification {

    static final ARTIFACT_SELECTION_FAILURES = [AMBIGUOUS_ARTIFACT_TRANSFORM, NO_COMPATIBLE_ARTIFACT, AMBIGUOUS_ARTIFACTS, UNKNOWN_ARTIFACT_SELECTION_FAILURE]

    def groups = TestUtil.problemsService().groups

    def "#id is reported in Dependencies > Artifact Resolution, named by its display name"() {
        when:
        def problemId = id.problemId(groups)

        then:
        problemId.group == groups.dependencies.artifactResolution
        problemId.name == id.displayName
        problemId.displayName == id.displayName

        where:
        id << ARTIFACT_SELECTION_FAILURES
    }

    def "#id is reported in Dependencies > Graph Resolution, named by its display name"() {
        when:
        def problemId = id.problemId(groups)

        then:
        problemId.group == groups.dependencies.graphResolution
        problemId.name == id.displayName
        problemId.displayName == id.displayName

        where:
        id << (ResolutionFailureProblemId.values() - ARTIFACT_SELECTION_FAILURES)
    }
}
