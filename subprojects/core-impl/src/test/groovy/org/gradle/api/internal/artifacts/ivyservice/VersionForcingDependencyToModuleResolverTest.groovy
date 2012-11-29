/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice

import org.apache.ivy.core.module.descriptor.DependencyDescriptor
import org.apache.ivy.core.module.id.ModuleId
import org.apache.ivy.core.module.id.ModuleRevisionId
import spock.lang.Specification
import org.gradle.api.Action

class VersionForcingDependencyToModuleResolverTest extends Specification {
    final target = Mock(DependencyToModuleVersionIdResolver)
    final resolvedVersion = Mock(ModuleVersionIdResolveResult)
    final forced = new ModuleRevisionId(new ModuleId('group', 'module'), 'forced')

    def "passes through dependency when it does not match any rule"() {
        def dep = dependency('org', 'module', '1.0')
        def rule = Mock(Action)
        def resolver = new VersionForcingDependencyToModuleResolver(target, [rule, rule])

        when:
        def result = resolver.resolve(dep)

        then:
        result == resolvedVersion

        and:
        1 * target.resolve(dep) >> resolvedVersion
        2 * rule.execute( {it.module.group == 'org' && it.module.name == 'module' && it.module.version == '1.0'} )
        0 * target._
    }

    def "replaces dependency according to first matching rule"() {
        def dep = dependency('org', 'module', '0.5')
        def modified = dependency('org', 'module', '1.0')

        def force1 = { it.forceVersion("1.0") } as Action
        def force2 = { it.forceVersion("2.0") } as Action

        def resolver = new VersionForcingDependencyToModuleResolver(target, [force1, force2])

        when:
        ForcedModuleVersionIdResolveResult result = resolver.resolve(dep)

        then:
        result.result == resolvedVersion
        result.selectionReason == ModuleVersionIdResolveResult.IdSelectionReason.forced

        and:
        1 * dep.clone(new ModuleRevisionId(new ModuleId('org', 'module'), '1.0')) >> modified
        1 * target.resolve(modified) >> resolvedVersion
        0 * target._
    }

    def dependency(String group, String module, String version) {
        Mock(DependencyDescriptor) { getDependencyRevisionId() >> new ModuleRevisionId(new ModuleId(group, module), version) }
    }
}
