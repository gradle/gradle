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
package org.gradle.api.internal.artifacts.dependencies

import org.gradle.api.InvalidUserCodeException
import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.DependencyArtifact
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.attributes.Attribute
import org.gradle.api.internal.artifacts.DefaultExcludeRule
import org.gradle.api.internal.artifacts.dsl.CapabilityNotationParserFactory
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.util.AttributeTestUtil
import org.gradle.util.TestUtil
import org.gradle.util.internal.WrapUtil
import spock.lang.Specification

abstract class AbstractModuleDependencySpec extends Specification {

    private ExternalModuleDependency dependency

    def setup() {
        dependency = createDependency("org.gradle", "gradle-core", "4.4-beta2")
    }

    protected ExternalModuleDependency createDependency(String group, String name, String version) {
        def dependency = createDependency(group, name, version, null)
        if (dependency instanceof AbstractModuleDependency) {
            dependency.attributesFactory = AttributeTestUtil.attributesFactory()
            dependency.objectFactory = TestUtil.objectFactory()
            dependency.capabilityNotationParser = new CapabilityNotationParserFactory(true).create()
        }
        dependency
    }

    protected abstract ExternalModuleDependency createDependency(String group, String name, String version, String configuration);

    void "has reasonable default values"() {
        expect:
        dependency.group == "org.gradle"
        dependency.name == "gradle-core"
        dependency.version == "4.4-beta2"
        dependency.versionConstraint.preferredVersion == ""
        dependency.versionConstraint.requiredVersion == "4.4-beta2"
        dependency.versionConstraint.strictVersion == ""
        dependency.versionConstraint.rejectedVersions == []
        dependency.transitive
        dependency.artifacts.isEmpty()
        dependency.excludeRules.isEmpty()
        dependency.targetConfiguration == null
        dependency.attributes == ImmutableAttributes.EMPTY
    }

    def "cannot create with null name"() {
        when:
        createDependency("group", null, "version")

        then:
        def e = thrown InvalidUserDataException
        e.message == "Name must not be null!"
    }

    def "artifact defaults to the dependency name"() {
        when:
        def dep = createDependency("group", "name", "version")
        dep.artifact {
            classifier = 'test'
        }

        then:
        dep.artifacts[0].name == 'name'
        dep.artifacts[0].classifier == 'test'
        dep.artifacts[0].type == 'jar'
    }

    void "can exclude dependencies"() {
        def excludeArgs1 = WrapUtil.toMap("group", "aGroup")
        def excludeArgs2 = WrapUtil.toMap("module", "aModule")

        when:
        dependency.exclude(excludeArgs1)
        dependency.exclude(excludeArgs2)

        then:
        dependency.excludeRules.size() == 2
        dependency.excludeRules.contains(new DefaultExcludeRule("aGroup", null))
        dependency.excludeRules.contains(new DefaultExcludeRule(null, "aModule"))
    }

    void "can add artifacts"() {
        def artifact1 = Mock(DependencyArtifact)
        def artifact2 = Mock(DependencyArtifact)

        when:
        dependency.addArtifact(artifact1)
        dependency.addArtifact(artifact2)

        then:
        dependency.artifacts.size() == 2
        dependency.artifacts.contains(artifact1)
        dependency.artifacts.contains(artifact2)
    }

    void "can set attributes"() {
        def attr1 = Attribute.of("attr1", String)
        def attr2 = Attribute.of("attr2", Integer)

        when:
        dependency.attributes {
            it.attribute(attr1, 'foo')
            it.attribute(attr2, 123)
        }

        then:
        dependency.attributes.keySet() == [attr1, attr2] as Set
        dependency.attributes.getAttribute(attr1) == 'foo'
        dependency.attributes.getAttribute(attr2) == 123
    }

    void "knows if is equal to"() {
        when:
        def dep1 = createDependency("group1", "name1", "version1")
        def dep2 = createDependency("group1", "name1", "version1")
        def attr1 = Attribute.of("attr1", String)
        def attr2 = Attribute.of("attr2", Integer)
        dep1.attributes {
            it.attribute(attr1, 'foo')
        }
        dep2.attributes {
            it.attribute(attr2, 123)
        }

        then:
        createDependency("group1", "name1", "version1") == createDependency("group1", "name1", "version1")
        createDependency("group1", "name1", "version1").hashCode() == createDependency("group1", "name1", "version1").hashCode()
        createDependency("group1", "name1", "version1") != createDependency("group1", "name1", "version2")
        createDependency("group1", "name1", "version1") != createDependency("group1", "name2", "version1")
        createDependency("group1", "name1", "version1") != createDependency("group2", "name1", "version1")
        createDependency("group1", "name1", "version1") != createDependency("group2", "name1", "version1")
        createDependency("group1", "name1", "version1", "depConf1") != createDependency("group1", "name1", "version1", "depConf2")

        dep1 != dep2

    }

    void "refuses artifact when attributes present"() {
        given:
        def dep = createDependency("group", "name", "1.0")
        dep.attributes {
            it.attribute(Attribute.of("attribute", String), 'foo')
        }

        when:
        dep.artifact {
            println("Not reached")
        }

        then:
        thrown(InvalidUserCodeException)

        when:
        dep.addArtifact(Mock(DependencyArtifact))

        then:
        thrown(InvalidUserCodeException)
    }

    void "refuses target configuration when attributes present"() {
        given:
        def dep = createDependency("group", "name", "1.0")
        dep.attributes {
            it.attribute(Attribute.of("attribute", String), 'foo')
        }

        when:
        dep.setTargetConfiguration('foo')

        then:
        thrown(InvalidUserCodeException)
    }

    void "refuses artifact when capability present"() {
        given:
        def dep = createDependency("group", "name", "1.0")
        dep.capabilities {
            it.requireCapability((Object)'org:foo:1.0')
        }

        when:
        dep.artifact {
            println("Not reached")
        }

        then:
        thrown(InvalidUserCodeException)

        when:
        dep.addArtifact(Mock(DependencyArtifact))

        then:
        thrown(InvalidUserCodeException)
    }

    void "refuses target configuration when capability present"() {
        given:
        def dep = createDependency("group", "name", "1.0")
        dep.capabilities {
            it.requireCapability((Object)'org:foo:1.0')
        }

        when:
        dep.setTargetConfiguration('foo')

        then:
        thrown(InvalidUserCodeException)
    }

    void "refuses attribute when targetConfiguration specified"() {
        given:
        def dep = createDependency("group", "name", "1.0")
        dep.setTargetConfiguration('foo')

        when:
        dep.attributes {
            it.attribute(Attribute.of("attribute", String), 'foo')
        }

        then:
        thrown(InvalidUserCodeException)
    }

    void "refuses capability when targetConfiguration specified"() {
        given:
        def dep = createDependency("group", "name", "1.0")
        dep.setTargetConfiguration('foo')

        when:
        dep.capabilities {
            it.requireCapability('org:foo:1.0')
        }

        then:
        thrown(InvalidUserCodeException)
    }

    void "refuses attribute when artifact added"() {
        given:
        def dep = createDependency("group", "name", "1.0")
        dep.addArtifact(Mock(DependencyArtifact))

        when:
        dep.attributes {
            it.attribute(Attribute.of("attribute", String), 'foo')
        }

        then:
        thrown(InvalidUserCodeException)
    }

    void "refuses capability when artifact added"() {
        given:
        def dep = createDependency("group", "name", "1.0")
        dep.addArtifact(Mock(DependencyArtifact))

        when:
        dep.capabilities {
            it.requireCapability('org:foo:1.0')
        }

        then:
        thrown(InvalidUserCodeException)
    }

    void "refuses configuration when artifact added"() {
        given:
        def dep = createDependency("group", "name", "1.0")
        dep.addArtifact(Mock(DependencyArtifact))

        when:
        dep.setTargetConfiguration('foo')

        then:
        thrown(InvalidUserCodeException)
    }

    void "refuses artifact when configuration specified"() {
        given:
        def dep = createDependency("group", "name", "1.0")
        dep.setTargetConfiguration('foo')

        when:
        dep.addArtifact(Mock(DependencyArtifact))

        then:
        thrown(InvalidUserCodeException)

        when:
        dep.artifact {
            throw new AssertionError()
        }

        then:
        thrown(InvalidUserCodeException)
    }

    void "copy does not mutate original attributes"() {
        def attr1 = Attribute.of("attr1", String)
        dependency.attributes {
            it.attribute(attr1, 'foo')
        }

        when:
        def copy = dependency.copy()
        copy.attributes {
            it.attribute(attr1, 'bar')
        }

        then:
        dependency.attributes.keySet() == [attr1] as Set
        dependency.attributes.getAttribute(attr1) == 'foo'

        copy.attributes.keySet() == [attr1] as Set
        copy.attributes.getAttribute(attr1) == 'bar'
    }

    void "copy does not mutate original capabilities"() {
        dependency.capabilities {
            it.requireCapability('org:original:1')
        }
        def parsedCapability = dependency.requestedCapabilities[0]

        when:
        def copy = dependency.copy()
        copy.capabilities {
            it.requireCapability('org:copy:1')
        }

        then:
        dependency.requestedCapabilities == [parsedCapability]
        copy.requestedCapabilities.size() == 2
        copy.requestedCapabilities[0] == parsedCapability
        copy.requestedCapabilities[1].name == 'copy'

    }

    def "creates deep copy"() {
        when:
        def copy = dependency.copy()

        then:
        assertDeepCopy(dependency, copy)

        when:
        dependency.transitive = false
        copy = dependency.copy()

        then:
        assertDeepCopy(dependency, copy)
    }

    static void assertDeepCopy(ModuleDependency dependency, ModuleDependency copiedDependency) {
        assert copiedDependency.group == dependency.group
        assert copiedDependency.name == dependency.name
        assert copiedDependency.version == dependency.version
        assert copiedDependency.targetConfiguration == dependency.targetConfiguration
        assert copiedDependency.transitive == dependency.transitive
        assert copiedDependency.artifacts == dependency.artifacts
        assert copiedDependency.excludeRules == dependency.excludeRules
        assert copiedDependency.attributes == dependency.attributes
        assert copiedDependency.requestedCapabilities == dependency.requestedCapabilities

        assert copiedDependency.attributes.is(ImmutableAttributes.EMPTY) || !copiedDependency.attributes.is(dependency.attributes)
        assert !copiedDependency.artifacts.is(dependency.artifacts)
        assert !copiedDependency.excludeRules.is(dependency.excludeRules)
    }
}
