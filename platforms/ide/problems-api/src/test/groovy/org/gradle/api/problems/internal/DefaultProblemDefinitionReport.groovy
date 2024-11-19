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

package org.gradle.api.problems.internal

import com.google.common.collect.ImmutableList
import org.gradle.api.problems.ProblemDefinition
import org.gradle.api.problems.Severity
import org.gradle.api.problems.SharedProblemGroup
import org.gradle.internal.deprecation.Documentation
import spock.lang.Specification

class DefaultProblemDefinitionReport extends Specification {
    def "equal objects are equal"() {
        when:
        def pd1 = createProblemDefinition()
        def pd2 = createProblemDefinition()

        then:
        pd1 == pd2
        pd1 == pd1
    }

    def "Changed label causes equal to be false"() {

        when:
        def pd1 = createProblemDefinition()
        def pd2 = createProblemDefinition("otherLabel")

        then:
        pd1 != pd2
    }

    def "Changed severity causes equal to be false"() {

        when:
        def pd1 = createProblemDefinition()
        def pd2 = createProblemDefinition("label", Severity.WARNING)

        then:
        pd1 != pd2
    }

    def "Changed documentation causes equal to be false"() {

        when:
        def pd1 = createProblemDefinition()
        def pd2 = createProblemDefinition("label", Severity.ERROR, Documentation.userManual("other"))

        then:
        pd1 != pd2
    }

    def "Changed category causes equal to be false"() {
        when:
        def pd1 = createProblemDefinition()
        def pd2 = createProblemDefinition("label2", Severity.ERROR, asdfManual, ImmutableList.of())

        then:
        pd1 != pd2
    }

    def static asdfManual = Documentation.userManual("asdf")


    ProblemDefinition createProblemDefinition(
        String label = "label",
        Severity severity = Severity.ERROR,
        Documentation documentation = asdfManual,
        List<String> solutions = ImmutableList.of(),
        String displayName = "display name"
    ) {
            new DefaultProblemDefinition(new DefaultProblemId(label, displayName, SharedProblemGroup.generic()), severity, documentation)
    }
}
