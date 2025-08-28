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

package org.gradle.api.internal.artifacts.repositories.resolver

import org.gradle.api.artifacts.DirectDependencyMetadata
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.attributes.Attribute
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.notations.DependencyMetadataNotationParser
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector
import org.gradle.internal.component.external.model.ModuleDependencyMetadata
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.util.AttributeTestUtil
import org.gradle.util.TestUtil
import org.gradle.util.internal.SimpleMapInterner
import spock.lang.Issue
import spock.lang.Specification

abstract class DependenciesMetadataAdapterTest extends Specification {
    DirectDependenciesMetadataAdapter adapter

    abstract ModuleDependencyMetadata newDependency(ModuleComponentSelector requested);

    def setup() {
        fillDependencyList(0)
    }

    @Issue("https://github.com/gradle/gradle/issues/20510")
    def "can read to removed element"() {
        given:
        adapter.add "org.gradle.test:module1:1.0"
        adapter.add "org.gradle.test:module2:1.0"

        when:
        List<DirectDependencyMetadata> found = adapter.findAll { (it.name == "module1") }
        adapter.removeAll(found)

        then:
        found.first().name == "module1"
    }

    @Issue("https://github.com/gradle/gradle/issues/20510")
    def "can remove and add dependencies"() {
        given:
        adapter.add "org.gradle.test:module1:1.0"
        adapter.add "org.gradle.test:module2:1.0"

        when:
        List<DirectDependencyMetadata> found = adapter.findAll { (it.name == "module1") }
        adapter.removeAll(found)
        found.each { adapter.add("$it.group:$it.name:2.0") }

        then:
        dependenciesMetadata.size() == 2
        dependenciesMetadata[0].selector.group == "org.gradle.test"
        dependenciesMetadata[0].selector.module == "module2"
        dependenciesMetadata[0].selector.version == "1.0"

        dependenciesMetadata[1].selector.group == "org.gradle.test"
        dependenciesMetadata[1].selector.module == "module1"
        dependenciesMetadata[1].selector.version == "2.0"
    }

    def "add via string id is propagate to the underlying dependency list"() {
        when:
        adapter.add "org.gradle.test:module1:1.0"

        then:
        dependenciesMetadata.size() == 1
        dependenciesMetadata[0].selector.group == "org.gradle.test"
        dependenciesMetadata[0].selector.module == "module1"
        dependenciesMetadata[0].selector.version == "1.0"
    }

    def "add via map id propagate to the underlying dependency list"() {
        when:
        adapter.add group: "org.gradle.test", name: "module1", version: "1.0"

        then:
        dependenciesMetadata.size() == 1
        dependenciesMetadata[0].selector.group == "org.gradle.test"
        dependenciesMetadata[0].selector.module == "module1"
        dependenciesMetadata[0].selector.version == "1.0"
    }

    def "add via string id with action is propagate to the underlying dependency list"() {
        when:
        adapter.add("org.gradle.test:module1") {
            it.version { it.require '1.0' }
            it.endorseStrictVersions()
        }

        then:
        dependenciesMetadata.size() == 1
        dependenciesMetadata[0].selector.group == "org.gradle.test"
        dependenciesMetadata[0].selector.module == "module1"
        dependenciesMetadata[0].selector.version == "1.0"
        dependenciesMetadata[0].isEndorsingStrictVersions()
    }

    def "add via map id with action propagate to the underlying dependency list"() {
        when:
        adapter.add(group: "org.gradle.test", name: "module1") {
            it.version { it.require '1.0' }
        }

        then:
        dependenciesMetadata.size() == 1
        dependenciesMetadata[0].selector.group == "org.gradle.test"
        dependenciesMetadata[0].selector.module == "module1"
        dependenciesMetadata[0].selector.version == "1.0"
    }

    def "remove is propagated to the underlying dependency list"() {
        given:
        fillDependencyList(1)

        when:
        adapter.removeAll { true }

        then:
        dependenciesMetadata == []
    }

    def "can add a dependency with the same coordinates more than once"() {
        //this test documents given behavior, which is not necessarily needed
        given:
        fillDependencyList(1)

        when:
        adapter.add("org.gradle.test:module0:2.0")

        then:
        dependenciesMetadata.size() == 2
        dependenciesMetadata[0].selector.group == "org.gradle.test"
        dependenciesMetadata[0].selector.module == "module0"
        dependenciesMetadata[0].selector.version == "1.0"
        dependenciesMetadata[1].selector.group == "org.gradle.test"
        dependenciesMetadata[1].selector.module == "module0"
        dependenciesMetadata[1].selector.version == "2.0"
    }

    def "iterator returns views on underlying list items"() {
        given:
        fillDependencyList(1)

        when:
        def dependencyMetadata = ++adapter.iterator()

        then:
        dependencyMetadata instanceof DirectDependencyMetadataAdapter
        !(dependencyMetadata instanceof DirectDependencyMetadataImpl)
    }

    def "can modify underlying list items"() {
        given:
        fillDependencyList(1)

        when:
        adapter.get(0).version { it.require "2.0" }

        then:
        dependenciesMetadata.size() == 1
        dependenciesMetadata[0].selector.group == "org.gradle.test"
        dependenciesMetadata[0].selector.module == "module0"
        dependenciesMetadata[0].selector.version == "2.0"
    }

    def "can modify dependency attributes"() {
        given:
        def attr = Attribute.of('test', String)
        fillDependencyList(1)

        when:
        adapter.get(0).attributes { it.attribute(attr, 'foo') }

        then:
        dependenciesMetadata.size() == 1
        dependenciesMetadata[0].selector.group == "org.gradle.test"
        dependenciesMetadata[0].selector.module == "module0"
        dependenciesMetadata[0].selector.version == "1.0"
        dependenciesMetadata[0].selector.attributes.keySet() == [attr] as Set
        dependenciesMetadata[0].selector.attributes.getAttribute(attr) == 'foo'
    }

    def "can modify dependency inheritance state"() {
        given:
        fillDependencyList(1)

        when:
        adapter.get(0).endorseStrictVersions()

        then:
        dependenciesMetadata.size() == 1
        dependenciesMetadata[0].isEndorsingStrictVersions()
    }

    def "modified dependency has no artifact selectors"() {
        when:
        adapter.add "org.gradle.test:module1:1.0"

        then:
        adapter.get(0).artifactSelectors == []
    }

    private fillDependencyList(int size) {
        adapter = new DirectDependenciesMetadataAdapter(
            AttributeTestUtil.attributesFactory(),
            TestUtil.instantiatorFactory().decorateLenient(),
            DependencyMetadataNotationParser.parser(DirectInstantiator.INSTANCE, DirectDependencyMetadataImpl.class, SimpleMapInterner.notThreadSafe()))

        for (int i = 0; i < size; i++) {
            ModuleComponentSelector requested = DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId("org.gradle.test", "module$i"), "1.0")
            ModuleDependencyMetadata dep = newDependency(requested)
            adapter.add ( DirectInstantiator.INSTANCE.newInstance(DirectDependencyMetadataAdapter, AttributeTestUtil.attributesFactory(), dep))
        }
    }

    List<ModuleDependencyMetadata> getDependenciesMetadata() {
        return adapter.getMetadatas()
    }
}
