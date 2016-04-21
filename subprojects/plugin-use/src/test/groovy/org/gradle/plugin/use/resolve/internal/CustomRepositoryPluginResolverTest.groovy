/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.internal.artifacts.DependencyResolutionServices
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionSelectorScheme
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.MavenVersionSelectorScheme
import org.gradle.groovy.scripts.StringScriptSource
import org.gradle.plugin.use.internal.DefaultPluginRequest
import org.gradle.plugin.use.internal.PluginRequest
import spock.lang.Specification

class CustomRepositoryPluginResolverTest extends Specification {
    def versionSelectorScheme = new MavenVersionSelectorScheme(new DefaultVersionSelectorScheme())
    def dependencyResolutionServices = Stub(DependencyResolutionServices) {
        getResolveRepositoryHandler() >> Stub(RepositoryHandler) {
            get(0) >> Stub(MavenArtifactRepository) {
                getName() >> "maven"
            }
        }
    }
    def result = Mock(PluginResolutionResult)

    def resolver = new CustomRepositoryPluginResolver(dependencyResolutionServices, versionSelectorScheme);

    PluginRequest request(String id, String version = null) {
        new DefaultPluginRequest(id, version, 1, new StringScriptSource("test", "test"))
    }

    def "fail pluginRequests without versions"() {
        when:
        resolver.resolve(request("plugin"), result)

        then:
        1 * result.notFound("maven", "plugin dependency must include a version number for this source")
    }

    def "fail pluginRequests with SNAPSHOT versions"() {
        when:
        resolver.resolve(request("plugin", "1.1-SNAPSHOT"), result)

        then:
        1 * result.notFound("maven", "snapshot plugin versions are not supported")
    }

    def "fail pluginRequests with dynamic versions"() {
        when:
        resolver.resolve(request("plugin", "latest.revision"), result)

        then:
        1 * result.notFound("maven", "dynamic plugin versions are not supported")
    }
}
