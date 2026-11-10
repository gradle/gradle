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


import org.gradle.api.problems.GradleProblemGroup
import org.gradle.api.problems.GradleSecondLevelProblemGroup
import org.gradle.api.problems.ProblemGroup
import org.gradle.api.problems.ProblemGroups
import org.gradle.api.problems.ProblemId
import org.gradle.api.problems.internal.location.CompiledCodeFrame
import org.gradle.api.problems.internal.location.CompiledCodePosition
import org.gradle.internal.Describables
import org.gradle.problems.Location
import org.gradle.problems.ProblemDiagnostics
import org.gradle.api.problems.internal.location.ClassFrame
import org.gradle.api.problems.internal.location.FileLocationFrame
import org.gradle.api.problems.internal.location.PropertyFrame
import org.gradle.internal.isolation.IsolatableFactory
import org.gradle.internal.problems.NoOpProblemDiagnosticsFactory
import org.gradle.internal.reflect.Instantiator
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer
import spock.lang.Specification

import static org.gradle.internal.problems.NoOpProblemDiagnosticsFactory.EMPTY_STREAM

class DefaultProblemBuilderTest extends Specification {

    def problemGroup = ProblemGroup.create("group", "label")
    def problemId = ProblemId.create('id', 'Problem Id', problemGroup)
    def buildLogicGroup = ProblemGroup.create('Build Logic', 'Build Logic')
    def groups = Stub(ProblemGroups) {
        getGradle() >> Stub(GradleProblemGroup) {
            getBuildLogic() >> Stub(GradleSecondLevelProblemGroup) {
                problemId(_) >> { String name -> new DefaultProblemId(name, name, buildLogicGroup) }
            }
        }
    }

    def 'additionalData accepts GeneralDataInternalSpec'() {
        given:
        def problemBuilder = createProblemBuilder()

        when:
        def data = problemBuilder
            .id(problemId)
            .additionalDataInternal(GeneralDataSpec, spec -> {
                spec.put("key", "value")
            })
            .build().additionalData

        then:
        GeneralData.isInstance(data)
    }

    DefaultProblemBuilder createProblemBuilder() {
        new DefaultProblemBuilder(new ProblemsInfrastructure(new AdditionalDataBuilderFactory(), Mock(Instantiator.class), Mock(PayloadSerializer.class), Mock(IsolatableFactory), Mock(IsolatableToBytesSerializer), EMPTY_STREAM, groups))
    }

    def 'additionalData accepts DeprecationDataInternalSpec'() {
        given:
        def problemBuilder = createProblemBuilder()

        when:
        def data = problemBuilder
            .id(problemId)
            .additionalDataInternal(DeprecationDataSpec, spec -> {
                spec.type(DeprecationData.Type.USER_CODE_INDIRECT)
            })
            .build().additionalData

        then:
        DeprecationData.isInstance(data)
    }

    def 'additionalData accepts TypeValidationDataInternalSpec'() {
        given:
        def problemBuilder = createProblemBuilder()

        when:
        def data = problemBuilder
            .id(problemId)
            .additionalDataInternal(TypeValidationDataSpec, spec -> {
                spec.propertyName("propertyName")
                spec.parentPropertyName("parentPropertyName")
                spec.pluginId("pluginId")
                spec.typeName("typeName")
            })
            .build().additionalData

        then:
        TypeValidationData.isInstance(data)
    }

    def 'additionalData accepts PropertyTraceDataInternalSpec'() {
        given:
        def problemBuilder = createProblemBuilder()

        when:
        def data = problemBuilder
            .id(problemId)
            .additionalDataInternal(PropertyTraceDataSpec, spec -> {
                spec.trace("trace")
            })
            .build().additionalData

        then:
        PropertyTraceData.isInstance(data)
    }


    def 'additionalDataInternal fails with invalid type'() {
        given:
        def problemBuilder = createProblemBuilder()


        when:
        //noinspection GroovyAssignabilityCheck
        def problem = problemBuilder
            .id(problemId)
            .additionalDataInternal(NoOpProblemDiagnosticsFactory, spec -> {
                // won't reach here

            })
            .build()
        def data = problem.additionalData

        then:
        data == null
        problem.definition.id.name == 'Unsupported additional data type in reported problem'
        problem.definition.id.group == buildLogicGroup
    }

    def 'problem without id is reported in Build Logic'() {
        when:
        def problem = createProblemBuilder().build()

        then:
        problem.definition.id.name == 'Reported problem has no id'
        problem.definition.id.group == buildLogicGroup
    }

    def "keeps Gradle-owned group instances when building from name and group"() {
        given:
        def group = new DescribedGroup("Root", "A described root")
        def child = new DescribedGroup("Child", "A described child", group)

        when:
        def problem = createProblemBuilder().id("name", "display", child).build()

        then:
        problem.definition.id.group.is(child)
        problem.definition.id.group.description == "A described child"
        problem.definition.id.group.parent.is(group)
    }

    def "copies foreign group implementations into Gradle-owned groups"() {
        given:
        def foreignRoot = new ForeignGroup("Root", null)
        def foreignChild = new ForeignGroup("Child", foreignRoot)

        when:
        def problem = createProblemBuilder().id("name", "display", foreignChild).build()

        then:
        def group = problem.definition.id.group
        !group.is(foreignChild)
        group instanceof DefaultProblemGroup
        group == foreignChild
        group.parent instanceof DefaultProblemGroup
        group.parent == foreignRoot
    }

    def "a Gradle-owned group never has a foreign ancestor: create() copies the foreign parent chain"() {
        given:
        def foreignRoot = new ForeignGroup("Root", null)
        def ownedChild = ProblemGroup.create("Child", "Child", foreignRoot)

        expect:
        ownedChild.parent instanceof DefaultProblemGroup
        !ownedChild.parent.is(foreignRoot)
        ownedChild.parent == foreignRoot
        ((ProblemGroupInternal) ownedChild).parentInternal.is(ownedChild.parent)

        when:
        def problem = createProblemBuilder().id("name", "display", ownedChild).build()

        then:
        // nothing left to copy, the builder keeps the instance
        problem.definition.id.group.is(ownedChild)
    }

    def "copies foreign problem id implementations, keeping Gradle-owned parent groups"() {
        given:
        def group = new DescribedGroup("Root", "A described root")
        def foreignId = new ForeignId("name", group)

        when:
        def problem = createProblemBuilder().id(foreignId).build()

        then:
        problem.definition.id instanceof DefaultProblemId
        problem.definition.id == foreignId
        problem.definition.id.group.is(group)
    }

    def "copies the foreign group of a Gradle-owned problem id"() {
        given:
        def foreignRoot = new ForeignGroup("Root", null)
        def ownedId = ProblemId.create("name", "display", foreignRoot)

        when:
        def problem = createProblemBuilder().id(ownedId).build()

        then:
        !problem.definition.id.is(ownedId)
        problem.definition.id == ownedId
        problem.definition.id.group instanceof DefaultProblemGroup
        problem.definition.id.group == foreignRoot
    }

    def "keeps a Gradle-owned problem id whose group is Gradle-owned"() {
        given:
        def ownedId = ProblemId.create("name", "display", ProblemGroup.create("Root", "Root"))

        expect:
        createProblemBuilder().id(ownedId).build().definition.id.is(ownedId)
    }

    private static class DescribedGroup extends ProblemGroup implements ProblemGroupInternal {
        final String name
        final String description
        final ProblemGroup parent

        DescribedGroup(String name, String description, ProblemGroup parent = null) {
            this.name = name
            this.description = description
            this.parent = parent
        }

        String getDisplayName() { name }

        String getDisplayNameInternal() { name }

        ProblemGroupInternal getParentInternal() { (ProblemGroupInternal) parent }

        boolean equals(Object o) { ProblemGroupInternal.structurallyEquals(this, o) }

        int hashCode() { ProblemGroupInternal.structuralHashCode(this) }
    }

    private static class ForeignGroup extends ProblemGroup {
        final String name
        final ProblemGroup parent

        ForeignGroup(String name, ProblemGroup parent) {
            this.name = name
            this.parent = parent
        }

        String getDisplayName() { name }

        boolean equals(Object o) { ProblemGroupInternal.structurallyEquals(this, o) }

        int hashCode() { Objects.hash(name, parent) }
    }

    private static class ForeignId extends ProblemId {
        final String name
        final ProblemGroup group

        ForeignId(String name, ProblemGroup group) {
            this.name = name
            this.group = group
        }

        String getDisplayName() { name }

        boolean equals(Object o) { o instanceof ProblemId && name == o.name && group == o.group }

        int hashCode() { Objects.hash(name, group) }
    }

    def "can define contextual locations"() {
        given:
        def problemBuilder = createProblemBuilder()

        when:
        //noinspection GroovyAssignabilityCheck
        def problem = problemBuilder
            .id(problemId)
            .taskLocation(":taskPath")
            .build()


        then:
        problem.contextualLocations.every { it instanceof TaskLocation }
        problem.contextualLocations.collect { (it as TaskLocation).buildTreePath } == [':taskPath']
    }

    def "newlines from contextual labels are removed"() {
        given:
        def problemBuilder = createProblemBuilder()

        when:
        //noinspection GroovyAssignabilityCheck
        def problem = problemBuilder
            .id(problemId)
            .contextualLabel("line1\nline2\r\nline3")
            .build()

        then:
        problem.contextualLabel == "line1 line2 line3"
    }

    def "each file location the reporter gives is an origin trace with that location as its leaf"() {
        when:
        def problem = createProblemBuilder()
            .id(problemId)
            .lineInFileLocation("build.gradle", 12, 5)
            .offsetInFileLocation("settings.gradle", 40, 3)
            .fileLocation("gradle.properties")
            .build()

        then:
        problem.originTraces.size() == 3
        problem.originTraces.every { it.frames.size() == 1 && it.frames[0] instanceof FileLocationFrame }
        problem.originTraces*.frames*.displayName.flatten() == ["build.gradle:12:5", "settings.gradle", "gradle.properties"]

        and: "line and column count from 1, offset from 0, and a missing value is null"
        with(problem.originTraces[0].frames[0].position) {
            path == "build.gradle"
            line == 12
            column == 5
            offset == null
            length == null
        }
        with(problem.originTraces[1].frames[0].position) {
            offset == 40
            length == 3
            line == null
        }
        with(problem.originTraces[2].frames[0].position) {
            line == null
            column == null
            offset == null
            length == null
        }

        and: "the flat list is still filled"
        problem.originLocations.size() == 3
    }

    def "the property and the type of a validation problem are its origin"() {
        when:
        def problem = createProblemBuilder()
            .id(problemId)
            .additionalDataInternal(TypeValidationDataSpec) {
                it.typeName("com.acme.AcmeTask").propertyName("bar")
            }
            .build()

        then:
        problem.originTraces.size() == 1
        problem.originTraces[0].frames*.displayName == ["property bar", "class com.acme.AcmeTask"]
        problem.originTraces[0].frames*.class == [PropertyFrame, ClassFrame]
        problem.originTraces[0].frames*.position == [null, null]
    }

    def "a nested property names its parent"() {
        when:
        def problem = createProblemBuilder()
            .id(problemId)
            .additionalDataInternal(TypeValidationDataSpec) {
                it.typeName("com.acme.AcmeTask").parentPropertyName("options").propertyName("bar")
            }
            .build()

        then:
        problem.originTraces[0].frames*.displayName == ["property options.bar", "class com.acme.AcmeTask"]
    }

    def "a problem without file locations or validation data has no origin"() {
        expect:
        createProblemBuilder().id(problemId).build().originTraces.empty
    }

    // Guava's ImmutableList stands in for a plugin's API: its package is com.google.common.collect, which Gradle doesn't treat as internal
    private static final Class<?> API = com.google.common.collect.ImmutableList

    def "callerLocation takes the first frame outside Gradle and outside the API's package as the origin leaf"() {
        given:
        def stack = [
            frame("org.gradle.api.problems.internal.DefaultProblemReporter", "report", "DefaultProblemReporter.java", 58),
            frame("com.google.common.collect.ImmutableList", "of", "ImmutableList.java", 20),
            frame("com.google.common.collect.internal.Checks", "run", "Checks.java", 7),
            frame("com.acme.util.Helper", "callCheck", "Helper.java", 31),
            frame("build_abc", "run", "build.gradle", 12),
        ]

        when:
        def problem = createProblemBuilder()
            .id(problemId)
            .diagnostics(diagnostics(stack, null))
            .callerLocation(API)
            .build()

        then:
        problem.originTraces.size() == 1
        with(problem.originTraces[0].frames[0]) {
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

    def "callerLocation keeps the script line when the caller is the script Gradle located"() {
        given:
        def stack = [
            frame("com.google.common.collect.ImmutableList", "of", "ImmutableList.java", 20),
            frame("build_abc", "run", "build.gradle", 12),
        ]
        def scriptLocation = new Location(Describables.of("build file 'build.gradle'"), Describables.of("build file"), "/work/build.gradle", 12)

        when:
        def problem = createProblemBuilder()
            .id(problemId)
            .diagnostics(diagnostics(stack, scriptLocation))
            .callerLocation(API)
            .build()

        then:
        problem.originTraces[0].frames[0] instanceof FileLocationFrame
        problem.originTraces[0].frames[0].displayName == "/work/build.gradle:12"
    }

    def "callerLocation falls back to the script line when no frame is outside the API"() {
        given:
        def stack = [frame("com.google.common.collect.ImmutableList", "of", "ImmutableList.java", 20)]
        def scriptLocation = new Location(Describables.of("build file 'build.gradle'"), Describables.of("build file"), "/work/build.gradle", 3)

        expect:
        createProblemBuilder().id(problemId).diagnostics(diagnostics(stack, scriptLocation)).callerLocation(API).build()
            .originTraces[0].frames[0].displayName == "/work/build.gradle:3"
    }

    private static StackTraceElement frame(String className, String method, String file, int line) {
        new StackTraceElement(className, method, file, line)
    }

    private ProblemDiagnostics diagnostics(List<StackTraceElement> stack, Location location) {
        Stub(ProblemDiagnostics) {
            getStack() >> stack
            getLocation() >> location
            getException() >> null
            getFailure() >> null
            getSource() >> null
        }
    }
}
