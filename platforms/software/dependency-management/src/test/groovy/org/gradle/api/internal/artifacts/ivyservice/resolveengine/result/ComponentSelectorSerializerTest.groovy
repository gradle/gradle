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
import org.gradle.api.capabilities.Capability
import org.gradle.api.internal.artifacts.DefaultBuildIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.ImmutableVersionConstraint
import org.gradle.api.internal.artifacts.dependencies.DefaultImmutableVersionConstraint
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.project.ProjectIdentity
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector
import org.gradle.internal.component.external.model.DefaultImmutableCapability
import org.gradle.internal.component.local.model.DefaultLibraryComponentSelector
import org.gradle.internal.component.local.model.DefaultProjectComponentSelector
import org.gradle.internal.component.local.model.ProjectComponentSelectorInternal
import org.gradle.internal.component.local.model.TestComponentIdentifiers
import org.gradle.internal.serialize.SerializerSpec
import org.gradle.util.AttributeTestUtil
import org.gradle.util.Path
import org.gradle.util.TestUtil

import static org.gradle.util.Path.path

class ComponentSelectorSerializerTest extends SerializerSpec {
    private final ComponentSelectorSerializer serializer = new ComponentSelectorSerializer(new DesugaredAttributeContainerSerializer(AttributeTestUtil.attributesFactory(), TestUtil.objectInstantiator()))

    private static ImmutableVersionConstraint constraint(String version, String preferredVersion = '', String strictVersion = '', List<String> rejectVersions = [], String branch = null) {
        return new DefaultImmutableVersionConstraint(
            preferredVersion,
            version,
            strictVersion,
            rejectVersions,
            branch
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
        def selector = new DefaultProjectComponentSelector(new ProjectIdentity(new DefaultBuildIdentifier(path(":build")), Path.ROOT, Path.ROOT, "rootProject"), ImmutableAttributes.EMPTY, capabilities())

        when:
        def result = serialize(selector, serializer) as ProjectComponentSelector

        then:
        result.buildPath == selector.buildPath
        result.projectPath == selector.projectPath
        result.requestedCapabilities == selector.requestedCapabilities
        assertSameProjectId(result as ProjectComponentSelectorInternal, selector)
    }

    def "serializes root build ProjectComponentSelector"() {
        given:
        def selector = new DefaultProjectComponentSelector(new ProjectIdentity(new DefaultBuildIdentifier(path(":build")), path(":a:b"), path(":a:b"), "b"), ImmutableAttributes.EMPTY, capabilities())

        when:
        def result = serialize(selector, serializer) as ProjectComponentSelector

        then:
        result.buildPath == selector.buildPath
        result.projectPath == selector.projectPath
        result.requestedCapabilities == selector.requestedCapabilities
        assertSameProjectId(result as ProjectComponentSelectorInternal, selector)
    }

    def "serializes other build root ProjectComponentSelector"() {
        given:
        def selector = new DefaultProjectComponentSelector(new ProjectIdentity(new DefaultBuildIdentifier(path(":build")), path(":prefix"), Path.ROOT, "someProject"), ImmutableAttributes.EMPTY, capabilities())

        when:
        def result = serialize(selector, serializer) as ProjectComponentSelector

        then:
        result.buildPath == selector.buildPath
        result.projectPath == selector.projectPath
        result.requestedCapabilities == selector.requestedCapabilities
        assertSameProjectId(result as ProjectComponentSelectorInternal, selector)
    }

    def "serializes other build ProjectComponentSelector"() {
        given:
        def selector = new DefaultProjectComponentSelector(new ProjectIdentity(new DefaultBuildIdentifier(path(":build")), path(":prefix:a:b"), path(":a:b"), "b"), ImmutableAttributes.EMPTY, capabilities())

        when:
        def result = serialize(selector, serializer) as ProjectComponentSelector

        then:
        result.buildPath == selector.buildPath
        result.projectPath == selector.projectPath
        result.requestedCapabilities == selector.requestedCapabilities
        assertSameProjectId(result as ProjectComponentSelectorInternal, selector)
    }

    def "serializes ProjectComponentSelector with attributes"() {
        given:
        def selector = new DefaultProjectComponentSelector(new ProjectIdentity(new DefaultBuildIdentifier(path(":build")), identityPath, projectPath, projectName), AttributeTestUtil.attributes(foo: 'x', bar: 'y'), capabilities())

        when:
        def result = serialize(selector, serializer) as ProjectComponentSelector

        then:
        result.buildPath == selector.buildPath
        result.projectPath == selector.projectPath
        result.attributes.getAttribute(Attribute.of('foo', String)) == 'x'
        result.attributes.getAttribute(Attribute.of('bar', String)) == 'y'
        result.requestedCapabilities == selector.requestedCapabilities
        assertSameProjectId(result as ProjectComponentSelectorInternal, selector)

        where:
        identityPath                  | projectPath  | projectName
        path(":prefix:a:b")           | path(":a:b") | 'b'
        path(":prefix:a:someProject") | Path.ROOT    | "someProject"
        Path.ROOT                     | Path.ROOT    | "rootProject"
    }

    def "serializes ModuleComponentSelector"() {
        given:
        ModuleComponentSelector selector = DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId('group-one', 'name-one'), constraint('version-one'), ImmutableAttributes.EMPTY, capabilities())

        when:
        ModuleComponentSelector result = serialize(selector, serializer)

        then:
        result.group == 'group-one'
        result.module == 'name-one'
        result.version == 'version-one'
        result.versionConstraint.requiredVersion == 'version-one'
        result.versionConstraint.preferredVersion == ''
        result.versionConstraint.strictVersion == ''
        result.versionConstraint.rejectedVersions == []
        result.requestedCapabilities == selector.requestedCapabilities
    }

    def "serializes BuildComponentSelector"() {
        given:
        ProjectComponentSelector selection = TestComponentIdentifiers.newSelector(':myPath')

        when:
        ProjectComponentSelector result = serialize(selection, serializer)

        then:
        result.projectPath == ':myPath'
    }

    def "serializes LibraryComponentSelector project #projectPath library #libraryName"() {
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
        ModuleComponentSelector selection = DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId('group-one', 'name-one'), constraint('req', 'pref', 'strict', ['rej']))

        when:
        ModuleComponentSelector result = serialize(selection, serializer)

        then:
        result.group == 'group-one'
        result.module == 'name-one'
        result.version == 'req'
        result.versionConstraint.requiredVersion == 'req'
        result.versionConstraint.preferredVersion == 'pref'
        result.versionConstraint.strictVersion == 'strict'
        result.versionConstraint.rejectedVersions == ['rej']
    }

    def "serializes version constraint with branch"() {
        given:
        ModuleComponentSelector selection = DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId('group-one', 'name-one'), constraint('', '', '', [], "custom-branch"))

        when:
        ModuleComponentSelector result = serialize(selection, serializer)

        then:
        result.group == 'group-one'
        result.module == 'name-one'
        result.version == ''
        result.versionConstraint.requiredVersion == ''
        result.versionConstraint.preferredVersion == ''
        result.versionConstraint.strictVersion == ''
        result.versionConstraint.rejectedVersions == []
        result.versionConstraint.branch == 'custom-branch'
    }

    def "serializes attributes"() {
        given:

        ModuleComponentSelector selector1 = DefaultModuleComponentSelector.newSelector(
            DefaultModuleIdentifier.newId('group1', 'name1'), constraint('1.0'), AttributeTestUtil.attributes("foo": "val1"), [])
        ModuleComponentSelector selector2 = DefaultModuleComponentSelector.newSelector(
            DefaultModuleIdentifier.newId('group2', 'name2'), constraint('1.0'), AttributeTestUtil.attributes("foo": "val2"), [])
        ModuleComponentSelector selector3 = DefaultModuleComponentSelector.newSelector(
            DefaultModuleIdentifier.newId('group3', 'name3'), constraint('1.0'), AttributeTestUtil.attributes("foo": "val1"), [])

        when:
        ModuleComponentSelector result1 = serialize(selector1, serializer)
        ModuleComponentSelector result2 = serialize(selector2, serializer)
        ModuleComponentSelector result3 = serialize(selector3, serializer)

        then:
        result1.attributes == AttributeTestUtil.attributes("foo": "val1")
        result2.attributes == AttributeTestUtil.attributes("foo": "val2")
        result3.attributes == AttributeTestUtil.attributes("foo": "val1")

    }

    def "de-duplicates attributes"() {
        def factory = AttributeTestUtil.attributesFactory()
        def attr = Attribute.of("foo", String)

        given:
        ModuleComponentSelector selector1 = DefaultModuleComponentSelector.newSelector(
            DefaultModuleIdentifier.newId('group1', 'name1'), constraint('1.0'), factory.of(attr, "val1"), [])
        ModuleComponentSelector selector2 = DefaultModuleComponentSelector.newSelector(
            DefaultModuleIdentifier.newId('group2', 'name2'), constraint('1.0'), factory.of(attr, "val2"), [])
        ModuleComponentSelector selector3 = DefaultModuleComponentSelector.newSelector(
            DefaultModuleIdentifier.newId('group3', 'name3'), constraint('1.0'), factory.of(attr, "val1"), [])

        when:
        byte[] result1 = toBytes(selector1, serializer)
        byte[] result2 = toBytes(selector2, serializer)
        byte[] result3 = toBytes(selector3, serializer)

        then:
        result2.length == result1.length // different attributes
        result3.length < result1.length // already seen

    }

    void assertSameProjectId(ProjectComponentSelectorInternal result, ProjectComponentSelectorInternal selector) {
        assert result.projectIdentity.buildIdentifier == selector.projectIdentity.buildIdentifier
        assert result.projectIdentity.buildTreePath == selector.projectIdentity.buildTreePath
        assert result.projectIdentity.projectPath == selector.projectIdentity.projectPath
        assert result.projectIdentity.projectName == selector.projectIdentity.projectName
    }

    private static List<Capability> capabilities() {
        [new DefaultImmutableCapability("org", "foo", "${Math.random()}"), new DefaultImmutableCapability("org", "bar", "${Math.random()}")]
    }
}
