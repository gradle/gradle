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


import org.gradle.api.problems.ProblemGroup
import org.gradle.api.problems.ProblemId
import org.gradle.internal.isolation.IsolatableFactory
import org.gradle.internal.problems.NoOpProblemDiagnosticsFactory
import org.gradle.internal.reflect.Instantiator
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer
import spock.lang.Specification

import static org.gradle.internal.problems.NoOpProblemDiagnosticsFactory.EMPTY_STREAM

class DefaultProblemBuilderTest extends Specification {

    def problemGroup = ProblemGroup.create("group", "label")
    def problemId = ProblemId.create('id', 'Problem Id', problemGroup)

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
        new DefaultProblemBuilder(new ProblemsInfrastructure(new AdditionalDataBuilderFactory(), Mock(Instantiator.class), Mock(PayloadSerializer.class), Mock(IsolatableFactory), Mock(IsolatableToBytesSerializer), EMPTY_STREAM))
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

    def "copies Gradle-owned groups whose ancestors are foreign implementations"() {
        given:
        def foreignRoot = new ForeignGroup("Root", null)
        def ownedChild = ProblemGroup.create("Child", "Child", foreignRoot)

        when:
        def problem = createProblemBuilder().id("name", "display", ownedChild).build()

        then:
        def group = problem.definition.id.group
        !group.is(ownedChild)
        group == ownedChild
        group.parent instanceof DefaultProblemGroup
        group.parent == foreignRoot
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

        boolean equals(Object o) { ProblemGroupSupport.equals(this, o) }

        int hashCode() { ProblemGroupSupport.hashCode(this) }
    }

    private static class ForeignGroup extends ProblemGroup {
        final String name
        final ProblemGroup parent

        ForeignGroup(String name, ProblemGroup parent) {
            this.name = name
            this.parent = parent
        }

        String getDisplayName() { name }

        boolean equals(Object o) { ProblemGroupSupport.equals(this, o) }

        int hashCode() { ProblemGroupSupport.hashCode(this) }
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
}
