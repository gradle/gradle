/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.tooling.composite.internal

import org.gradle.tooling.model.build.BuildEnvironment
import org.gradle.tooling.model.build.GradleEnvironment
import org.gradle.util.TextUtil
import spock.lang.Specification

class VerifyMinimumGradleVersionTest extends Specification {

    def gradleVersions = [] as Set
    def reason = new StringBuilder()
    def spec = new DefaultCompositeValidator.VerifyMinimumGradleVersion(reason)

    def "fails and provides reason when a Gradle participant is older than 1.0"() {
        gradleVersions.addAll([ gradleVersion("1.0-milestone-2"), gradleVersion("1.0"), gradleVersion("2.0") ])

        expect:
        fails()
        TextUtil.normaliseLineSeparators(reason.toString()) == "Composite builds require Gradle 1.0 or newer. A project is configured to use 1.0-milestone-2, which is too old to be used in a composite.\n"
    }

    def "passes with Gradle versions newer than 1.0"() {
        gradleVersions.addAll([ "2.2.1", "2.5", "2.10", "1.2", "1.10" ].collect { gradleVersion(it) })
        expect:
        passes()
        reason.toString() == ""
    }

    def fails() {
        !passes()
    }

    def passes() {
        spec.isSatisfiedBy(gradleVersions)
    }

    BuildEnvironment gradleVersion(version) {
        BuildEnvironment buildEnvironment = Mock()
        GradleEnvironment gradleEnvironment = Mock()
        buildEnvironment.gradle >> gradleEnvironment
        gradleEnvironment.gradleVersion >> version
        return buildEnvironment
    }
}
