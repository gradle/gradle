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

import org.gradle.api.problems.AdditionalData
import org.gradle.api.problems.ProblemGroup
import org.gradle.api.problems.ProblemLocation
import org.gradle.api.problems.Severity
import org.gradle.api.problems.internal.DefaultFileLocation
import org.gradle.api.problems.internal.DefaultLineInFileLocation
import org.gradle.api.problems.internal.DefaultOffsetInFileLocation
import org.gradle.api.problems.internal.DefaultProblem
import org.gradle.api.problems.internal.DefaultProblemDefinition
import org.gradle.api.problems.internal.DefaultProblemId
import org.gradle.api.problems.internal.DefaultStackTraceLocation
import org.gradle.api.problems.internal.DefaultTypeValidationData
import org.gradle.api.problems.internal.ProblemInternal
import org.gradle.api.problems.internal.location.ClassFrame
import org.gradle.api.problems.internal.location.CompiledCodeFrame
import org.gradle.api.problems.internal.location.CompiledCodePosition
import org.gradle.api.problems.internal.location.FileLocationFrame
import org.gradle.api.problems.internal.location.PropertyFrame
import spock.lang.Specification

/**
 * The origin traces the runtime derives for a problem whose reporter gave flat locations only.
 */
class OriginTraceDerivationTest extends Specification {

    // Guava's ImmutableList stands in for a plugin's API: its package is com.google.common.collect, which Gradle doesn't treat as internal
    private static final String API = "com.google.common.collect.ImmutableList"

    def "each file location the reporter gives is an origin trace with that location as its leaf"() {
        when:
        def traces = OriginTraces.derivedFrom(problem([
            DefaultLineInFileLocation.from("build.gradle", 12, 5),
            DefaultOffsetInFileLocation.from("settings.gradle", 40, 3),
            DefaultFileLocation.from("gradle.properties")
        ]))

        then:
        traces.size() == 3
        traces.every { it.frames.size() == 1 && it.frames[0] instanceof FileLocationFrame }
        traces*.frames*.displayName.flatten() == [
            "build.gradle:12:5",
            "settings.gradle",
            "gradle.properties"
        ]

        and: "line and column count from 1, offset from 0, and a missing value is null"
        with(traces[0].frames[0].position) {
            path == "build.gradle"
            line == 12
            column == 5
            offset == null
            length == null
        }
        with(traces[1].frames[0].position) {
            offset == 40
            length == 3
            line == null
        }
        with(traces[2].frames[0].position) {
            line == null
            column == null
            offset == null
            length == null
        }
    }

    def "the property and the type of a validation problem are its origin"() {
        when:
        def traces = OriginTraces.derivedFrom(problem([], new DefaultTypeValidationData(null, "bar", null, null, "com.acme.AcmeTask")))

        then:
        traces.size() == 1
        traces[0].frames*.displayName == [
            "property bar",
            "class com.acme.AcmeTask"
        ]
        traces[0].frames*.class == [
            PropertyFrame,
            ClassFrame
        ]
        traces[0].frames*.position == [null, null]
    }

    def "a nested property names its parent"() {
        expect:
        OriginTraces.derivedFrom(problem([], new DefaultTypeValidationData(null, "bar", null, "options", "com.acme.AcmeTask")))[0].frames*.displayName == [
            "property options.bar",
            "class com.acme.AcmeTask"
        ]
    }

    def "a problem without file locations or validation data has no origin"() {
        expect:
        OriginTraces.derivedFrom(problem([])).empty
    }

    def "a named API makes the first frame outside Gradle and outside the API's package the origin leaf"() {
        given:
        def stack = [
            frame("org.gradle.api.problems.internal.DefaultProblemReporter", "report", "DefaultProblemReporter.java", 58),
            frame("com.google.common.collect.ImmutableList", "of", "ImmutableList.java", 20),
            frame("com.google.common.collect.internal.Checks", "run", "Checks.java", 7),
            frame("com.acme.util.Helper", "callCheck", "Helper.java", 31),
            frame("build_abc", "run", "build.gradle", 12),
        ]

        when:
        def traces = OriginTraces.derivedFrom(problem([new DefaultStackTraceLocation(null, stack, API)]))

        then:
        traces.size() == 1
        with(traces[0].frames[0]) {
            it instanceof CompiledCodeFrame
            displayName == "Helper.java:31"
            with(position as CompiledCodePosition) {
                fileName == "Helper.java"
                className == "com.acme.util.Helper"
                line == 31
                module == null
            }
        }
    }

    def "the script line stays when the caller of the named API is the script Gradle located"() {
        given:
        def stack = [
            frame("com.google.common.collect.ImmutableList", "of", "ImmutableList.java", 20),
            frame("build_abc", "run", "build.gradle", 12),
        ]

        when:
        def traces = OriginTraces.derivedFrom(problem([new DefaultStackTraceLocation(DefaultLineInFileLocation.from("/work/build.gradle", 12), stack, API)]))

        then:
        traces[0].frames[0] instanceof FileLocationFrame
        traces[0].frames[0].displayName == "/work/build.gradle:12"
    }

    def "the script line is the leaf when no frame is outside the named API"() {
        given:
        def stack = [frame("com.google.common.collect.ImmutableList", "of", "ImmutableList.java", 20)]

        expect:
        OriginTraces.derivedFrom(problem([new DefaultStackTraceLocation(DefaultLineInFileLocation.from("/work/build.gradle", 3), stack, API)]))[0].frames[0].displayName == "/work/build.gradle:3"
    }

    def "without a named API a captured stack gives the script line Gradle located, or nothing"() {
        given:
        def stack = [frame("com.acme.util.Helper", "callCheck", "Helper.java", 31)]

        expect:
        OriginTraces.derivedFrom(problem([new DefaultStackTraceLocation(DefaultLineInFileLocation.from("/work/build.gradle", 3), stack)]))[0].frames[0].displayName == "/work/build.gradle:3"
        OriginTraces.derivedFrom(problem([new DefaultStackTraceLocation(null, stack)])).empty
    }

    private static StackTraceElement frame(String className, String method, String file, int line) {
        new StackTraceElement(className, method, file, line)
    }

    private static ProblemInternal problem(List<ProblemLocation> originLocations, AdditionalData additionalData = null) {
        def definition = new DefaultProblemDefinition(new DefaultProblemId("p", "P", ProblemGroup.create("g", "G")), Severity.WARNING, null)
        new DefaultProblem(definition, "label", [], originLocations, [], null, null, additionalData, false)
    }
}
