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

package org.gradle.initialization

import org.gradle.api.internal.SettingsInternal
import org.gradle.api.internal.file.BaseDirFileResolver
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.plugins.DefaultPluginContainer
import org.gradle.api.internal.plugins.DefaultPluginRegistry
import org.gradle.api.internal.plugins.PluginRegistry
import org.gradle.api.plugins.PluginContainer
import org.gradle.internal.service.ServiceRegistry
import spock.lang.Specification

import static org.hamcrest.Matchers.sameInstance

class SettingsInternalServiceRegistryTest extends Specification {
    private SettingsInternal settings = Mock()
    private ServiceRegistry parent = Stub()
    private SettingsInternalServiceRegistry registry = new SettingsInternalServiceRegistry(parent, settings)

    def setup() {
        settings.getSettingsDir() >> new File("settings-dir").absoluteFile
        parent.get(org.gradle.internal.nativeplatform.filesystem.FileSystem) >> Stub(org.gradle.internal.nativeplatform.filesystem.FileSystem)
    }


    def "provides a plugin container"() {
        when:
        def pluginContainer = registry.get(PluginContainer)
        def secondPluginContainer = registry.get(PluginContainer)

        then:
        pluginContainer instanceof DefaultPluginContainer
        secondPluginContainer sameInstance(pluginContainer)
    }

    def "provides a file resolver"() {
        when:
        def fileResolver = registry.get(FileResolver)
        def secondFileResolver = registry.get(FileResolver)

        then:
        fileResolver instanceof BaseDirFileResolver
        secondFileResolver sameInstance(fileResolver)
    }


    def "provides a plugin registry"() {
        when:
        def pluginRegistry = registry.get(PluginRegistry)
        def secondPluginRegistry = registry.get(PluginRegistry)

        then:
        pluginRegistry instanceof DefaultPluginRegistry
        secondPluginRegistry sameInstance(pluginRegistry)
    }
}
