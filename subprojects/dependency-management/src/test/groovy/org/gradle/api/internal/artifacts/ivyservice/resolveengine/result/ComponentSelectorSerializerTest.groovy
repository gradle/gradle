/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.result

import org.gradle.api.artifacts.component.LibraryComponentSelector
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.component.ProjectComponentSelector
import org.gradle.api.attributes.Attribute
import org.gradle.api.internal.artifacts.DefaultBuildIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.ImmutableVersionConstraint
import org.gradle.api.internal.artifacts.dependencies.DefaultImmutableVersionConstraint
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.model.NamedObjectInstantiator
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector
import org.gradle.internal.component.local.model.DefaultLibraryComponentSelector
import org.gradle.internal.component.local.model.DefaultProjectComponentSelector
import org.gradle.internal.component.local.model.TestComponentIdentifiers
import org.gradle.internal.serialize.SerializerSpec
import org.gradle.util.Path
import org.gradle.util.TestUtil
import spock.lang.Unroll

import static org.gradle.util.Path.path

class ComponentSelectorSerializerTest extends SerializerSpec {
    private final ComponentSelectorSerializer serializer = new ComponentSelectorSerializer(new DesugaredAttributeContainerSerializer(TestUtil.attributesFactory(), NamedObjectInstantiator.INSTANCE))

    private ImmutableVersionConstraint constraint(String version, String strictVersion = '', List<String> rejectVersions = []) {
        return new DefaultImmutableVersionConstraint(
            version,
            strictVersion,
            rejectVersions
        )
    }

    def "throws exception if null is provided"() {
        when:
        serialize(null, serializer)

        then:
        Throwable t = thrown(IllegalArgumentException)
        t.message == 'Provided component selector may not be null'
    }

    def "serializes root project ProjectComponentSelector"() {
        given:
        def selector = new DefaultProjectComponentSelector(new DefaultBuildIdentifier("build"), Path.ROOT, Path.ROOT, "rootProject", ImmutableAttributes.EMPTY)

        when:
        def result = serialize(selector, serializer)

        then:
        result.identityPath == selector.identityPath
        result.projectPath == selector.projectPath
        result.projectPath() == selector.projectPath()
        result.projectName == selector.projectName
    }

    def "serializes root build ProjectComponentSelector"() {
        given:
        def selector = new DefaultProjectComponentSelector(new DefaultBuildIdentifier("build"), path(":a:b"), path(":a:b"), "b", ImmutableAttributes.EMPTY)

        when:
        def result = serialize(selector, serializer)

        then:
        result.identityPath == selector.identityPath
        result.projectPath == selector.projectPath
        result.projectPath() == selector.projectPath()
        result.projectName == selector.projectName
    }

    def "serializes other build root ProjectComponentSelector"() {
        given:
        def selector = new DefaultProjectComponentSelector(new DefaultBuildIdentifier("build"), path(":prefix:a:someProject"), Path.ROOT, "someProject", ImmutableAttributes.EMPTY)

        when:
        def result = serialize(selector, serializer)

        then:
        result.identityPath == selector.identityPath
        result.projectPath == selector.projectPath
        result.projectPath() == selector.projectPath()
        result.projectName == selector.projectName
    }

    def "serializes other build ProjectComponentSelector"() {
        given:
        def selector = new DefaultProjectComponentSelector(new DefaultBuildIdentifier("build"), path(":prefix:a:b"), path(":a:b"), "b", ImmutableAttributes.EMPTY)

        when:
        def result = serialize(selector, serializer)

        then:
        result.identityPath == selector.identityPath
        result.projectPath == selector.projectPath
        result.projectPath() == selector.projectPath()
        result.projectName == selector.projectName
    }

    @Unroll
    def "serializes ProjectComponentSelector with attributes"() {
        given:
        def selector = new DefaultProjectComponentSelector(new DefaultBuildIdentifier(buildId), identityPath, projectPath, projectName, TestUtil.attributes(foo: 'x', bar: 'y'))

        when:
        def result = serialize(selector, serializer)

        then:
        result.identityPath == selector.identityPath
        result.projectPath == selector.projectPath
        result.projectPath() == selector.projectPath()
        result.projectName == selector.projectName
        result.attributes.getAttribute(Attribute.of('foo', String)) == 'x'
        result.attributes.getAttribute(Attribute.of('bar', String)) == 'y'

        where:
        buildId | identityPath                       | projectPath       | projectName
        'build' | path(":prefix:a:b")           | path(":a:b") | 'b'
        'build' | path(":prefix:a:someProject") | Path.ROOT         | "someProject"
        'build' | Path.ROOT                          | Path.ROOT         | "rootProject"

    }

    def "serializes ModuleComponentSelector"() {
        given:
        ModuleComponentSelector selection = DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId('group-one', 'name-one'), constraint('version-one'))

        when:
        ModuleComponentSelector result = serialize(selection, serializer)

        then:
        result.group == 'group-one'
        result.module == 'name-one'
        result.version == 'version-one'
        result.versionConstraint.preferredVersion == 'version-one'
        result.versionConstraint.rejectedVersions == []
    }

    def "serializes BuildComponentSelector"() {
        given:
        ProjectComponentSelector selection = TestComponentIdentifiers.newSelector(':myPath')

        when:
        ProjectComponentSelector result = serialize(selection, serializer)

        then:
        result.projectPath == ':myPath'
    }

    @Unroll
    def "serializes LibraryComponentSelector project #projectPath library #libraryName variant #variant"() {
        given:
        LibraryComponentSelector selection = new DefaultLibraryComponentSelector(projectPath, libraryName)

        when:
        LibraryComponentSelector result = serialize(selection, serializer)

        then:
        result.projectPath == projectPath
        result.libraryName == libraryName

        where:
        projectPath | libraryName
        ':myPath'   | null
        ':myPath'   | 'myLib'
        ':myPath'   | null
        ':myPath'   | 'myLib'
    }

    def "serializes version constraint"() {
        given:
        ModuleComponentSelector selection = DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId('group-one', 'name-one'), constraint('pref', 'req', ['rej']))

        when:
        ModuleComponentSelector result = serialize(selection, serializer)

        then:
        result.group == 'group-one'
        result.module == 'name-one'
        result.version == 'pref'
        result.versionConstraint.preferredVersion == 'pref'
        result.versionConstraint.strictVersion == 'req'
        result.versionConstraint.rejectedVersions == ['rej']
    }
}
