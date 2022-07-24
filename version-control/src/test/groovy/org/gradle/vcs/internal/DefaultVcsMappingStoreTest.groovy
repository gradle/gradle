/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.vcs.internal

import org.gradle.api.Action
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.internal.GradleInternal
import spock.lang.Specification
import org.gradle.vcs.VersionControlSpec

class DefaultVcsMappingStoreTest extends Specification {
    def factory = Mock(VcsMappingFactory)
    def store = new DefaultVcsMappingsStore(factory)

    def "does nothing when no rules defined"() {
        def selector = Stub(ModuleComponentSelector)

        when:
        def spec = store.locateVcsFor(selector)

        then:
        spec == null
        0 * factory._
    }

    def "does nothing when no rule accepts selector"() {
        def selector = Stub(ModuleComponentSelector)
        def mapping = new DefaultVcsMapping(selector, Stub(VersionControlSpecFactory))
        def rule = Mock(Action)

        store.addRule(rule, Stub(GradleInternal))

        when:
        def spec = store.locateVcsFor(selector)

        then:
        spec == null
        1 * factory.create(selector) >> mapping
        1 * rule.execute(mapping)
        0 * _
    }

    def "returns repo defined by last rule that accepts selector"() {
        def selector = Stub(ModuleComponentSelector)
        def mapping = new DefaultVcsMapping(selector, Stub(VersionControlSpecFactory))
        def rule = Mock(Action)
        def rule2 = Mock(Action)
        def vcsSpec = Stub(VersionControlSpec)
        def build = Stub(GradleInternal)

        store.addRule(rule, build)
        store.addRule(rule2, build)

        when:
        def spec = store.locateVcsFor(selector)

        then:
        spec == vcsSpec
        1 * factory.create(selector) >> mapping
        1 * rule.execute(mapping) >> {
            mapping.from(Stub(VersionControlSpec))
        }
        1 * rule2.execute(mapping) >> {
            mapping.from(vcsSpec)
        }
        0 * _
    }
}
