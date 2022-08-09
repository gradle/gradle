/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.test.fixtures

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import groovy.transform.Canonical
import groovy.transform.EqualsAndHashCode
import org.gradle.internal.hash.HashCode
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.gradle.ArtifactSelectorSpec
import org.gradle.util.GradleVersion

import javax.annotation.Nullable

class GradleModuleMetadata {
    private Map<String, Object> values
    private List<Variant> variants

    GradleModuleMetadata(TestFile file) {
        file.withReader { r ->
            JsonReader reader = new JsonReader(r)
            values = readObject(reader)
        }
        assert values.formatVersion == '1.1'
        assert values.createdBy.gradle.version == GradleVersion.current().version
        variants = (values.variants ?: []).collect { new Variant(it.name, it) }
    }

    @Nullable
    CreatedBy getCreatedBy() {
        def createdBy = values.createdBy
        if (createdBy == null) {
            return null
        }
        return new CreatedBy(createdBy.gradle.version, createdBy.gradle.buildId)
    }

    @Nullable
    Coords getComponent() {
        def comp = values.component
        if (comp == null || comp.url) {
            return null
        }
        return new Coords(comp.group, comp.module, comp.version)
    }

    @Nullable
    ModuleReference getOwner() {
        def comp = values.component
        if (comp == null || !comp.url) {
            return null
        }
        return new ModuleReference(comp.group, comp.module, comp.version, comp.url)
    }

    Map<String, String> getAttributes() {
        values.component?.attributes
    }

    List<Variant> getVariants() {
        return variants
    }

    Variant variant(String name) {
        def matches = variants.findAll { it.name == name }
        assert matches.size() == 1 : "Variant '$name' not found in ${variants.name}"
        return matches.first()
    }

    Variant variant(String name, @DelegatesTo(value=Variant, strategy=Closure.DELEGATE_FIRST) Closure<Variant> action) {
        def variant = variant(name)
        action.delegate = variant
        action.resolveStrategy = Closure.DELEGATE_FIRST
        action()
        variant
    }

    private Map<String, Object> readObject(JsonReader reader) {
        Map<String, Object> values = [:]
        reader.beginObject()
        while (reader.peek() != JsonToken.END_OBJECT) {
            String name = reader.nextName()
            Object value = readAny(reader)
            values.put(name, value)
        }
        reader.endObject()
        return values
    }

    private Object readAny(JsonReader reader) {
        Object value = null
        switch (reader.peek()) {
            case JsonToken.NULL:
                reader.nextNull()
                break
            case JsonToken.STRING:
                value = reader.nextString()
                break
            case JsonToken.BOOLEAN:
                value = reader.nextBoolean()
                break
            case JsonToken.NUMBER:
                value = reader.nextLong()
                break
            case JsonToken.BEGIN_OBJECT:
                value = readObject(reader)
                break
            case JsonToken.BEGIN_ARRAY:
                value = readArray(reader)
                break
        }
        value
    }

    private List<Object> readArray(JsonReader reader) {
        List<Object> values = []
        reader.beginArray()
        while (reader.peek() != JsonToken.END_ARRAY) {
            Object value = readAny(reader)
            values.add(value)
        }
        reader.endArray()
        return values
    }

    static Map<String, String> normalizeForTests(Map<String, ?> attributes) {
        attributes?.collectEntries { k, v -> [k, v?.toString()] }
    }

    static class Variant {
        final String name
        private final Map<String, Object> values

        List<Dependency> dependencies
        final Set<Dependency> checkedDependencies = []
        List<DependencyConstraint> dependencyConstraints
        final Set<DependencyConstraint> checkedDependencyConstraints = []
        List<Capability> capabilities
        final Set<Capability> checkedCapabilities = []

        Variant(String name, Map<String, Object> values) {
            this.name = name
            this.values = values
        }

        @Nullable
        ModuleReference getAvailableAt() {
            def ref = values['available-at']
            return ref == null ? null : new ModuleReference(ref.group, ref.module, ref.version, ref.url)
        }

        Map<String, String> getAttributes() {
            values.attributes
        }

        List<Dependency> getDependencies() {
            if (dependencies == null) {
                dependencies = (values.dependencies ?: []).collect {
                    def exclusions = it.excludes ? it.excludes.collect { "${it.group}:${it.module}" } : []
                    new Dependency(it.group, it.module, it.version?.requires, it.version?.prefers, it.version?.strictly, it.version?.rejects ?: [], exclusions, it.endorseStrictVersions, it.reason, it.thirdPartyCompatibility?.artifactSelector, normalizeForTests(it.attributes), it.requestedCapabilities)
                }
            }
            dependencies
        }

        Variant noMoreDependencies() {
            Set<Dependency> uncheckedDependencies = getDependencies() - checkedDependencies
            assert uncheckedDependencies.empty
            Set<Dependency> uncheckedDependencyConstraints = getDependencyConstraints() - checkedDependencyConstraints
            assert uncheckedDependencyConstraints.empty
            this
        }

        List<DependencyConstraint> getDependencyConstraints() {
            if (dependencyConstraints == null) {
                dependencyConstraints = (values.dependencyConstraints ?: []).collect {
                    new DependencyConstraint(it.group, it.module, it.version.requires, it.version.prefers, it.version.strictly, it.version.rejects ?: [], it.reason, normalizeForTests(it.attributes))
                }
            }
            dependencyConstraints
        }

        List<File> getFiles() {
            return (values.files ?: []).collect { new File(it.name, it.url, it.size, HashCode.fromString(it.sha1), HashCode.fromString(it.md5), HashCode.fromString(it.sha256), HashCode.fromString(it.sha512)) }
        }

        DependencyView dependency(String group, String module, String version, @DelegatesTo(value=DependencyView, strategy= Closure.DELEGATE_FIRST) Closure<Void> action = { exists() }) {
            def view = new DependencyView(group, module, version)
            action.delegate = view
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action()
            view
        }

        DependencyView dependency(String notation, @DelegatesTo(value=DependencyView, strategy= Closure.DELEGATE_FIRST) Closure<Void> action = { exists() }) {
            def (String group, String module, String version) = notation.split(':') as List
            dependency(group, module, version, action)
        }

        DependencyConstraintView constraint(String group, String module, String version, @DelegatesTo(value=DependencyView, strategy= Closure.DELEGATE_FIRST) Closure<Void> action = { exists() }) {
            def view = new DependencyConstraintView(group, module, version)
            action.delegate = view
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action()
            view
        }

        DependencyConstraintView constraint(String notation, @DelegatesTo(value=DependencyConstraintView, strategy= Closure.DELEGATE_FIRST) Closure<Void> action = { exists() }) {
            def (String group, String module, String version) = notation.split(':') as List
            constraint(group, module, version, action)
        }

        List<Capability> getCapabilities() {
            if (capabilities == null) {
                capabilities = (values.capabilities ?: []).collect {
                    new Capability(it.group, it.name, it.version)
                }
            }
            capabilities
        }

        Variant capability(String group, String name, String version) {
            def cap = getCapabilities().find { it.group == group && it.name == name && it.version == version}
            assert cap : "capability ${group}:${name}:${version} not found in ${getCapabilities()}"
            checkedCapabilities << cap
            this
        }

        Variant noMoreCapabilities() {
            Set<Capability> uncheckedCapabilities = getCapabilities() - checkedCapabilities
            assert uncheckedCapabilities.empty
            this
        }

        class DependencyView {
            final String group
            final String module
            final String version
            final Set<String> checkedExcludes = []
            final Set<Capability> checkedRequestedCapabilities = []
            final Iterator<Dependency> candidates

            Dependency current

            DependencyView(String gid, String mid, String v) {
                group = gid
                module = mid
                version = v
                candidates = dependencies.findAll { it.group == group && it.module == module && it.version == version }.iterator()
                next()
            }

            DependencyView isLast() {
                assert !candidates.hasNext()
                this
            }

            DependencyView next() {
                if (candidates.hasNext()) {
                    checkedExcludes.clear()
                    current = candidates.next()
                } else {
                    current = null
                }
                return this
            }

            Dependency find() {
                assert current : "dependency ${group}:${module}:${version} not found in $dependencies"
                checkedDependencies << current
                current
            }

            DependencyView exists() {
                assert find()
                this
            }

            DependencyView hasRequestedCapability(String group, String name, String version = null) {
                def capability = new Capability(group, name, version)
                assert find()?.requestedCapabilities?.contains(capability)
                checkedRequestedCapabilities << capability
                this
            }

            DependencyView noMoreCapabilities() {
                Set<Capability> uncheckedCapabilities = find()?.requestedCapabilities - checkedRequestedCapabilities
                assert uncheckedCapabilities.empty
                this
            }

            DependencyView hasExclude(String group, String module = '*') {
                String exc = "${group}:${module}"
                assert find()?.excludes?.contains(exc)
                checkedExcludes << exc
                this
            }

            DependencyView noMoreExcludes() {
                def uncheckedExcludes = find().excludes - checkedExcludes
                assert uncheckedExcludes.empty
                this
            }

            DependencyView notTransitive() {
                hasExclude('*', '*')
                noMoreExcludes()
            }

            DependencyView prefers(String version = null) {
                String actualPrefers = find()?.prefers
                assert actualPrefers == version
                this
            }

            DependencyView strictly(String version = null) {
                String actualStrictly = find()?.strictly
                assert actualStrictly == version
                this
            }

            DependencyView rejects(String... rejections) {
                Set<String> actualRejects = find()?.rejectsVersion
                Set<String> expectedRejects = rejections as Set
                assert actualRejects == expectedRejects
                this
            }

            DependencyView hasReason(String reason) {
                assert find()?.reason == reason
                this
            }

            DependencyView hasAttribute(String attribute) {
                assert find()?.attributes?.containsKey(attribute)
                this
            }

            DependencyView hasAttribute(String attribute, Object value) {
                String expected = value?.toString()
                assert find()?.attributes?.get(attribute) == expected
                this
            }

            DependencyView noAttributes() {
                def actualAttributes = find()?.attributes ?: [:]
                assert actualAttributes == [:]
            }

            DependencyView hasAttributes(Map<String, Object> fullAttributeSet) {
                Map<String, String> expectedAttributes = normalizeForTests(fullAttributeSet)
                def actualAttributes = find()?.attributes
                assert actualAttributes == expectedAttributes
                this
            }

            ArtifactSelectorSpec getArtifactSelector() {
                current.artifactSelector
            }
        }

        class DependencyConstraintView {
            final String group
            final String module
            final String version

            DependencyConstraintView(String gid, String mid, String v) {
                group = gid
                module = mid
                version = v
            }

            DependencyConstraint find() {
                def depConstraint = dependencyConstraints.find { it.group == group && it.module == module && it.version == version }
                assert depConstraint : "constraint ${group}:${module}:${version} not found in $dependencyConstraints"
                checkedDependencyConstraints << depConstraint
                depConstraint
            }

            DependencyConstraintView exists() {
                assert find()
                this
            }

            DependencyConstraintView prefers(String version) {
                String actualPrefers = find()?.prefers
                assert actualPrefers == version
                this
            }

            DependencyConstraintView strictly(String version) {
                String actualStrictly = find()?.strictly
                assert actualStrictly == version
                this
            }

            DependencyConstraintView rejects(String... rejections) {
                Set<String> actualRejects = find()?.rejectsVersion
                Set<String> expectedRejects = rejections as Set
                assert actualRejects == expectedRejects
                this
            }

            DependencyConstraintView hasReason(String reason) {
                assert find()?.reason == reason
                this
            }


            DependencyConstraintView hasAttribute(String attribute) {
                assert find()?.attributes?.containsKey(attribute)
                this
            }

            DependencyConstraintView hasAttribute(String attribute, Object value) {
                String expected = value?.toString()
                assert find()?.attributes[attribute] == expected
                this
            }

            DependencyConstraintView hasAttributes(Map<String, Object> fullAttributeSet) {
                Map<String, String> expectedAttributes = normalizeForTests(fullAttributeSet)
                def actualAttributes = find()?.attributes
                assert actualAttributes == expectedAttributes
                this
            }
        }
    }

    @EqualsAndHashCode
    static class CreatedBy {
        final String gradleVersion
        final String buildId

        CreatedBy(String gradleVersion, String buildId) {
            this.gradleVersion = gradleVersion
            this.buildId = buildId
        }
    }

    @EqualsAndHashCode
    static class Coords {
        final String group
        final String module
        final String version
        final String prefers
        final String strictly
        final List<String> rejectsVersion
        final String reason
        final Map<String, String> attributes

        Coords(String group, String module, String version, String prefers = '', String strictly = '', List<String> rejectsVersion = [], String reason = null, Map<String, String> attributes = [:]) {
            this.group = group
            this.module = module
            this.version = version
            this.prefers = prefers
            this.strictly = strictly
            this.rejectsVersion = rejectsVersion
            this.reason = reason
            this.attributes = attributes
        }

        String getCoords() {
            return "$group:$module:${version ?: prefers ?: ''}"
        }

        String toString() {
            coords
        }
    }

    @EqualsAndHashCode
    static class ModuleReference extends Coords {
        final String url

        ModuleReference(String group, String module, String version, String url) {
            super(group, module, version)
            this.url = url
        }

        String toString() {
            "${coords} ${url?url:''}"
        }
    }

    @EqualsAndHashCode
    static class Dependency extends Coords {
        final List<String> excludes
        final List<Capability> requestedCapabilities
        final boolean endorseStrictVersions
        final ArtifactSelectorSpec artifactSelector

        Dependency(String group, String module, String requires, String prefers, String strictly, List<String> rejectedVersions, List<String> excludes, Boolean endorseStrictVersions, String reason, Map<String, String> artifactSelector, Map<String, String> attributes, List<Map<String, String>> requestedCapabilities) {
            super(group, module, requires, prefers, strictly, rejectedVersions, reason, attributes)
            this.excludes = excludes*.toString()
            this.requestedCapabilities = requestedCapabilities.collect { new Capability(it.group, it.name, it.version) }
            this.endorseStrictVersions = endorseStrictVersions
            this.artifactSelector = artifactSelector != null ? new ArtifactSelectorSpec(artifactSelector.name, artifactSelector.type, artifactSelector.extesion, artifactSelector.classifier) : null
        }

        String toString() {
            def exc = ""
            if (excludes) {
                exc = excludes.collect { " excludes $it" }.join(', ')
            }
            "${coords}${exc}"
        }
    }

    @EqualsAndHashCode
    static class DependencyConstraint extends Coords {
        DependencyConstraint(String group, String module, String version, String prefers, String strictly, List<String> rejectedVersions, String reason, Map<String, String> attributes) {
            super(group, module, version, prefers, strictly, rejectedVersions, reason, attributes)
        }
    }

    static class File {
        final String name
        final String url
        final long size
        final HashCode sha1
        final HashCode sha256
        final HashCode sha512
        final HashCode md5

        File(String name, String url, long size, HashCode sha1, HashCode md5, HashCode sha256, HashCode sha512) {
            this.name = name
            this.url = url
            this.size = size
            this.sha1 = sha1
            this.md5 = md5
            this.sha256 = sha256
            this.sha512 = sha512
        }

        String toString() {
            "name($name) URL($url) size($size) sha1($sha1) sha256($sha256) sha512($sha512) md5($md5)"
        }
    }

    @Canonical
    static class Capability {
        String group
        String name
        String version

        Capability(String group, String name, String version) {
            this.group = group
            this.name = name
            this.version = version
        }
    }
}
