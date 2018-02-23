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

package org.gradle.plugin.use.resolve.internal

import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.internal.plugins.PluginInspector
import org.gradle.initialization.definition.SelfResolvingPluginRequest
import org.gradle.plugin.management.internal.DefaultPluginRequest
import spock.lang.Specification

class SelfResolvingRequestPluginResolverTest extends Specification {
    def resolver = new SelfResolvingRequestPluginResolver(Mock(PluginInspector))
    def result = Mock(PluginResolutionResult)

    def "resolves self-resolving requests"() {
        def pluginRequest = new SelfResolvingPluginRequest("id", Mock(ClassLoaderScope))
        when:
        resolver.resolve(pluginRequest, result)
        then:
        1 * result.found("injected from outer build", (PluginResolution)_)
    }

    def "ignores non-self-resolving requests"() {
        def pluginRequest = new DefaultPluginRequest("id", "version", false, null, "test")
        when:
        resolver.resolve(pluginRequest, result)
        then:
        0 * _
    }
}
