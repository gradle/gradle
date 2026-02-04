/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.factories

import groovy.transform.CompileStatic
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.simple.DefaultExcludeFactory
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec
import org.gradle.internal.collect.PersistentSet
import org.gradle.internal.component.model.DefaultIvyArtifactName
import org.gradle.internal.component.model.IvyArtifactName
import spock.lang.Shared

import static org.gradle.api.internal.artifacts.DefaultModuleIdentifier.newId

@CompileStatic
trait ExcludeTestSupport {

    @Shared
    ExcludeFactory factory = new DefaultExcludeFactory()

    ExcludeSpec group(String group) {
        factory.group(group)
    }

    ExcludeSpec module(String name) {
        factory.module(name)
    }

    ExcludeSpec moduleSet(String... names) {
        factory.moduleSet(randomizedPersistentSetOf(names as Set<String>))
    }

    ExcludeSpec groupSet(String... groups) {
        factory.groupSet(randomizedPersistentSetOf(groups as Set<String>))
    }

    ExcludeSpec moduleId(String group, String name) {
        factory.moduleId(newId(group, name))
    }

    ExcludeSpec moduleIdSet(List<String>... ids) {
        factory.moduleIdSet(randomizedPersistentSetOf(ids.collect { newId(it[0], it[1]) } as Set<ModuleIdentifier>))
    }

    ExcludeSpec moduleIdSet(String... ids) {
        factory.moduleIdSet(randomizedPersistentSetOf(ids.collect {
            def split = it.split(':')
            newId(split[0], split[1])
        } as Set<ModuleIdentifier>))
    }

    ExcludeSpec anyOf(ExcludeSpec... specs) {
        switch (specs.length) {
            case 0:
                return factory.nothing()
            case 1:
                return specs[0]
            case 2:
                return factory.anyOf(specs[0], specs[1])
            default:
                return factory.anyOf(randomizedPersistentSetOf(specs as Set<ExcludeSpec>))
        }
    }

    ExcludeSpec allOf(ExcludeSpec... specs) {
        switch (specs.length) {
            case 0:
                return factory.nothing()
            case 1:
                return specs[0]
            case 2:
                return factory.allOf(specs[0], specs[1])
            default:
                return factory.allOf(randomizedPersistentSetOf(specs as Set<ExcludeSpec>))
        }
    }

    ExcludeSpec nothing() {
        factory.nothing()
    }

    ExcludeSpec everything() {
        factory.everything()
    }

    ExcludeSpec ivy(String group, String module, IvyArtifactName artifact, String matcher) {
        factory.ivyPatternExclude(
            newId(group, module),
            artifact,
            matcher
        )
    }

    IvyArtifactName artifact(String name) {
        new DefaultIvyArtifactName(name, "jar", "jar")
    }

    <K> PersistentSet<K> psetOf(Iterable<K> ks) {
        PersistentSet.copyOf(ks)
    }

    private <K> PersistentSet<K> randomizedPersistentSetOf(Set<K> set) {
        psetOf(set.toList().shuffled())
    }
}
