/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.plugins.jvm.internal

import org.gradle.api.internal.artifacts.capability.FeatureCapabilitySelector
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependency
import org.gradle.api.internal.artifacts.dsl.CapabilityNotationParserFactory
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.tasks.DefaultTaskDependencyFactory
import org.gradle.api.plugins.jvm.TestFixturesDependencyModifiers
import org.gradle.util.TestUtil
import spock.lang.Specification

class TestFixturesDependencyModifiersTest extends Specification {
    def "copies given external dependency to select test fixtures"() {
        def modifier = TestUtil.objectFactory().newInstance(TestFixturesDependencyModifiers.TestFixturesDependencyModifier)
        def dependency = new DefaultExternalModuleDependency("group", "name", "1.0")
        dependency.setCapabilityNotationParser(new CapabilityNotationParserFactory(true).create())
        dependency.setObjectFactory(TestUtil.objectFactory())

        when:
        dependency = modifier.modify(dependency)
        then:
        dependency.getCapabilitySelectors().size() == 1
        dependency.getCapabilitySelectors()[0].with {
            assert it instanceof FeatureCapabilitySelector
            assert it.featureName == "test-fixtures"
        }
    }

    def "copies given project dependency to select test fixtures"() {
        def modifier = TestUtil.objectFactory().newInstance(TestFixturesDependencyModifiers.TestFixturesDependencyModifier)
        def projectInternal = Stub(ProjectInternal) {
            group >> "group"
            name >> "name"
            version >> "1.0"
        }
        def dependency = new DefaultProjectDependency(projectInternal, false, DefaultTaskDependencyFactory.withNoAssociatedProject())
        dependency.setCapabilityNotationParser(new CapabilityNotationParserFactory(true).create())
        dependency.setObjectFactory(TestUtil.objectFactory())

        when:
        dependency = modifier.modify(dependency)
        then:
        dependency.getCapabilitySelectors().size() == 1
        dependency.getCapabilitySelectors()[0].with {
            assert it instanceof FeatureCapabilitySelector
            assert it.featureName == "test-fixtures"
        }

        0 * _
    }

    def "does not modify given external dependency to select test fixtures"() {
        def modifier = TestUtil.objectFactory().newInstance(TestFixturesDependencyModifiers.TestFixturesDependencyModifier)
        def dependency = new DefaultExternalModuleDependency("group", "name", "1.0")
        dependency.setCapabilityNotationParser(new CapabilityNotationParserFactory(true).create())
        dependency.setObjectFactory(TestUtil.objectFactory())

        when:
        modifier.modify(dependency)
        then:
        dependency.getCapabilitySelectors().isEmpty()
    }

    def "does not modify given project dependency to select test fixtures"() {
        def modifier = TestUtil.objectFactory().newInstance(TestFixturesDependencyModifiers.TestFixturesDependencyModifier)
        def projectInternal = Stub(ProjectInternal) {
            group >> "group"
            name >> "name"
            version >> "1.0"
        }
        def dependency = new DefaultProjectDependency(projectInternal, false, DefaultTaskDependencyFactory.withNoAssociatedProject())
        dependency.setCapabilityNotationParser(new CapabilityNotationParserFactory(true).create())
        dependency.setObjectFactory(TestUtil.objectFactory())

        when:
        modifier.modify(dependency)
        then:
        dependency.getCapabilitySelectors().isEmpty()

        0 * _
    }
}
