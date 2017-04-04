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

import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.DependencyArtifact
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.internal.artifacts.DefaultExcludeRule
import org.gradle.util.WrapUtil
import spock.lang.Specification

abstract class AbstractModuleDependencySpec extends Specification {

    private dependency = createDependency("org.gradle", "gradle-core", "4.4-beta2")

    def init() {
        dependency = createDependency("org.gradle", "gradle-core", "4.4-beta2")
    }

    protected createDependency(String group, String name, String version) {
        createDependency(group, name, version, null)
    }

    protected abstract ExternalModuleDependency createDependency(String group, String name, String version, String configuration);

    void "has reasonable default values"() {
        expect:
        dependency.group == "org.gradle"
        dependency.name == "gradle-core"
        dependency.version == "4.4-beta2"
        dependency.transitive
        dependency.artifacts.isEmpty()
        dependency.excludeRules.isEmpty()
        dependency.targetConfiguration == null
    }

    def "cannot create with null name"() {
        when:
        createDependency("group", null, "version")

        then:
        def e = thrown InvalidUserDataException
        e.message == "Name must not be null!"
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

    void "knows if is equal to"() {
        expect:
        createDependency("group1", "name1", "version1") == createDependency("group1", "name1", "version1")
        createDependency("group1", "name1", "version1").hashCode() == createDependency("group1", "name1", "version1").hashCode()
        createDependency("group1", "name1", "version1") != createDependency("group1", "name1", "version2")
        createDependency("group1", "name1", "version1") != createDependency("group1", "name2", "version1")
        createDependency("group1", "name1", "version1") != createDependency("group2", "name1", "version1")
        createDependency("group1", "name1", "version1") != createDependency("group2", "name1", "version1")
        createDependency("group1", "name1", "version1", "depConf1") != createDependency("group1", "name1", "version1", "depConf2")
    }

    def "creates deep copy"() {
        when:
        def copy = dependency.copy()

        then:
        assertDeepCopy(dependency, copy)
    }

    public static void assertDeepCopy(ModuleDependency dependency, ModuleDependency copiedDependency) {
        assert copiedDependency.group == dependency.group
        assert copiedDependency.name == dependency.name
        assert copiedDependency.version == dependency.version
        assert copiedDependency.targetConfiguration == dependency.targetConfiguration
        assert copiedDependency.transitive == dependency.transitive
        assert copiedDependency.artifacts == dependency.artifacts
        assert copiedDependency.excludeRules == dependency.excludeRules

        assert !copiedDependency.artifacts.is(dependency.artifacts)
        assert !copiedDependency.excludeRules.is(dependency.excludeRules)
    }
}
