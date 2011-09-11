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

import spock.lang.Specification
import org.gradle.api.internal.artifacts.configurations.ResolverProvider
import org.apache.ivy.plugins.resolver.DependencyResolver
import org.apache.ivy.core.module.descriptor.ModuleDescriptor
import org.apache.ivy.Ivy
import org.apache.ivy.core.settings.IvySettings

class ResolveIvyFactoryTest extends Specification {
    final IvyFactory ivyFactory = Mock()
    final ResolverProvider resolverProvider = Mock()
    final SettingsConverter settingsConverter = Mock()
    final DependencyResolver internalRepo = Mock()
    final Map<String, ModuleDescriptor> clientModuleRegistry = [:]
    final ResolveIvyFactory factory = new ResolveIvyFactory(ivyFactory, resolverProvider, settingsConverter, internalRepo, clientModuleRegistry)

    def "creates Ivy instance"() {
        Ivy ivy = Mock()
        IvySettings ivySettings = Mock()
        DependencyResolver resolver1 = Mock()
        DependencyResolver resolver2 = Mock()

        when:
        def result = factory.create()

        then:
        result == ivy
        1 * resolverProvider.resolvers >> [resolver1, resolver2]
        1 * settingsConverter.convertForResolve([internalRepo, resolver1, resolver2], clientModuleRegistry) >> ivySettings
        1 * ivyFactory.createIvy(ivySettings) >> ivy
        0 * _._
    }
}
