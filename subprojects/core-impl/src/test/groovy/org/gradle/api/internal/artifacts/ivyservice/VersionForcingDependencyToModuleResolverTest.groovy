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
import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector
import spock.lang.Specification

class VersionForcingDependencyToModuleResolverTest extends Specification {
    final DependencyToModuleResolver target = Mock()
    final ModuleRevisionId forced = new ModuleRevisionId(new ModuleId('group', 'module'), 'forced')
    final VersionForcingDependencyToModuleResolver resolver = new VersionForcingDependencyToModuleResolver(target, [new DefaultModuleVersionSelector('group', 'module', 'forced')])

    def "passes through dependency when it does not match any forced group and module"() {
        ModuleVersionResolver version = Mock()
        def differentGroup = dependency('other', 'module')

        when:
        def result = resolver.create(differentGroup)

        then:
        result == version

        and:
        1 * target.create(differentGroup) >> version
    }

    def "replaces dependency when it matches a forced group and module"() {
        ModuleVersionResolver version = Mock()
        DependencyDescriptor modified = Mock()
        def dep = dependency('group', 'module')

        when:
        def result = resolver.create(dep)

        then:
        result == version

        and:
        1 * dep.clone(forced) >> modified
        1 * target.create(modified) >> version
    }

    def dependency(String group, String module) {
        DependencyDescriptor descriptor = Mock()
        descriptor.dependencyId >> new ModuleId(group, module)
        return descriptor
    }
}
