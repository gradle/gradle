/*
 * Copyright 2026 Gradle and contributors.
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

package org.gradle.plugin.use.resolve.internal

import org.gradle.internal.resolve.ArtifactNotFoundException
import org.gradle.internal.resolve.ModuleVersionNotFoundException
import org.gradle.internal.resolve.ModuleVersionResolveException
import spock.lang.Specification

import static org.gradle.plugin.use.resolve.internal.ReportableResolutionFailures.selectUnexpected

class ReportableResolutionFailuresTest extends Specification {

    def "selects nothing when every failure only means the module is missing"() {
        expect:
        selectUnexpected([
            Mock(ModuleVersionNotFoundException),
            Mock(ModuleVersionNotFoundException)
        ]).isEmpty()
    }

    def "selects nothing when every failure only means the artifact is missing"() {
        expect:
        selectUnexpected([Mock(ArtifactNotFoundException)]).isEmpty()
    }

    def "selects nothing when there are no failures"() {
        expect:
        selectUnexpected([]).isEmpty()
    }

    def "selects a resolve failure that is not one of the missing subtypes"() {
        given:
        // Matching on ModuleVersionResolveException instead of its subtypes would filter this out,
        // and every real failure with it, without any other test noticing.
        def failure = Mock(ModuleVersionResolveException)

        expect:
        selectUnexpected([failure]) == [failure]
    }

    def "selects a failure that is not a missing module"() {
        given:
        def failure = new RuntimeException("repository is on fire")

        expect:
        selectUnexpected([failure]) == [failure]
    }

    def "selects only the failures that are not missing modules"() {
        given:
        def failure = new RuntimeException("repository is on fire")

        expect:
        selectUnexpected([
            Mock(ModuleVersionNotFoundException),
            failure,
            Mock(ArtifactNotFoundException)
        ]) == [failure]
    }

    def "keeps every failure worth reporting, in the order they were given"() {
        given:
        def first = new RuntimeException("first repository is on fire")
        def second = new RuntimeException("second repository is on fire")

        expect:
        selectUnexpected([first, second]) == [first, second]
    }

    def "selects a failure whose cause is a missing module"() {
        given:
        // Only the top level is examined, so a not-found cause does not hide its parent.
        def notFound = Mock(ModuleVersionNotFoundException)
        def failure = new RuntimeException("repository is on fire", notFound)

        expect:
        selectUnexpected([failure]) == [failure]
    }
}
