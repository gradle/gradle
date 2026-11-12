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
import org.gradle.api.problems.Severity
import org.gradle.api.problems.internal.DefaultProblem
import org.gradle.api.problems.internal.DefaultProblemDefinition
import org.gradle.api.problems.internal.DefaultProblemId
import org.gradle.api.problems.internal.location.ClassFrame
import org.gradle.api.problems.internal.location.Frame
import org.gradle.api.problems.internal.location.PropertyFrame
import org.gradle.api.problems.internal.location.TaskFrame
import org.gradle.api.problems.internal.location.Trace
import org.gradle.internal.code.Locality
import spock.lang.Specification

class ProblemIdentityTest extends Specification {

    def "problems whose origin traces differ in an authored frame are different problems"() {
        expect:
        identity(field("a"), type("AcmeTask"), task(":x")) != identity(field("b"), type("AcmeTask"), task(":x"))
    }

    def "problems whose origin traces differ only in build model frames or in locality are the same problem"() {
        expect: "a field that a type declares is one problem, however many tasks of that type there are"
        identity(field("a"), type("AcmeTask"), task(":x")) == identity(field("a"), type("AcmeTask"), task(":y"))
        identity(field("a"), type("AcmeTask"), task(":x")).hashCode() == identity(field("a"), type("AcmeTask"), task(":y")).hashCode()
        identity(new PropertyFrame("field", "a", Locality.LOCAL)) == identity(field("a"))
    }

    def "a task counts when no type stands between it and the leaf"() {
        expect: "a property of one task is that task's, so the same name on another task is another problem"
        identity(field("a"), task(":x")) != identity(field("a"), task(":y"))
        identity(field("a"), task(":x")) == identity(field("a"), task(":x"))
        identity(field("a"), task(":x")).hashCode() == identity(field("a"), task(":x")).hashCode()

        and: "so is a task that is the leaf itself"
        identity(task(":x")) != identity(task(":y"))
    }

    def "problems without origin traces are told apart by what they say"() {
        expect:
        ProblemIdentity.of(problem("one")) == ProblemIdentity.of(problem("one"))
        ProblemIdentity.of(problem("one")) != ProblemIdentity.of(problem("another"))
    }

    private static Frame type(String name) {
        new ClassFrame(name, Locality.UNKNOWN)
    }

    private static Frame field(String name) {
        new PropertyFrame("field", name, Locality.UNKNOWN)
    }

    private static Frame task(String path) {
        new TaskFrame(path, Locality.UNKNOWN)
    }

    private static Object identity(Frame... frames) {
        ProblemIdentity.of(problem("label").withOriginTraces([new Trace(frames.toList())]))
    }

    private static DefaultProblem problem(String label) {
        def definition = new DefaultProblemDefinition(new DefaultProblemId("p", "P", ProblemGroup.create("g", "G")), Severity.WARNING, null)
        new DefaultProblem(definition, label, [], [], [])
    }
}
