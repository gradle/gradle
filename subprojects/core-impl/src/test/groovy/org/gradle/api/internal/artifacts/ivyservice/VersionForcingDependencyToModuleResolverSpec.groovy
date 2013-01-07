/*
 * Copyright 2012 the original author or authors.
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
import org.gradle.api.Action
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.VersionSelectionReasons
import spock.lang.Specification

class VersionForcingDependencyToModuleResolverSpec extends Specification {
    final target = Mock(DependencyToModuleVersionIdResolver)
    final resolvedVersion = Mock(ModuleVersionIdResolveResult)
    final forced = new ModuleRevisionId(new ModuleId('group', 'module'), 'forced')

    def "passes through dependency when it does not match any rule"() {
        def dep = dependency('org', 'module', '1.0')
        def rule = Mock(Action)
        def resolver = new VersionForcingDependencyToModuleResolver(target, rule)

        when:
        def result = resolver.resolve(dep)

        then:
        result == resolvedVersion

        and:
        1 * target.resolve(dep) >> resolvedVersion
        1 * rule.execute( {it.requested.group == 'org' && it.requested.name == 'module' && it.requested.version == '1.0'} )
        0 * target._
    }

    def "replaces dependency by action"() {
        def dep = dependency('org', 'module', '0.5')
        def modified = dependency('org', 'module', '1.0')

        def force = { it.useVersion("1.0") } as Action

        def resolver = new VersionForcingDependencyToModuleResolver(target, force)

        when:
        SubstitutedModuleVersionIdResolveResult result = resolver.resolve(dep)

        then:
        result.result == resolvedVersion
        result.selectionReason == VersionSelectionReasons.SELECTED_BY_ACTION

        and:
        1 * dep.clone(new ModuleRevisionId(new ModuleId('org', 'module'), '1.0')) >> modified
        1 * target.resolve(modified) >> resolvedVersion
        0 * target._
    }

    def "explosive action yields failure result that provides context"() {
        def force = { throw new Error("Boo!") } as Action
        def resolver = new VersionForcingDependencyToModuleResolver(target, force)

        when:
        def result = resolver.resolve(dependency('org', 'module', '0.5'))

        then:
        result.failure.message == "Could not resolve org:module:0.5."
        result.failure.cause.message == 'Boo!'
    }

    def "failed result uses correct exception"() {
        def force = { throw new Error("Boo!") } as Action
        def resolver = new VersionForcingDependencyToModuleResolver(target, force)
        def result = resolver.resolve(dependency('org', 'module', '0.5'))

        when:
        result.getId()
        then:
        def ex = thrown(ModuleVersionResolveException)
        ex == result.failure

        when:
        result.getSelectionReason()
        then:
        def ex2 = thrown(ModuleVersionResolveException)
        ex2 == result.failure

        when:
        result.resolve()
        then:
        def ex3 = thrown(ModuleVersionResolveException)
        ex3 == result.failure
    }

    def dependency(String group, String module, String version) {
        Mock(DependencyDescriptor) { getDependencyRevisionId() >> new ModuleRevisionId(new ModuleId(group, module), version) }
    }
}
