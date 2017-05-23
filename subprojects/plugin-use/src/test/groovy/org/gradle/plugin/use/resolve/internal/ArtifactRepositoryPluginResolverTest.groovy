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

import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionSelectorScheme
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.MavenVersionSelectorScheme
import org.gradle.groovy.scripts.StringScriptSource
import org.gradle.plugin.management.internal.DefaultPluginRequest
import spock.lang.Specification

class ArtifactRepositoryPluginResolverTest extends Specification {
    def versionSelectorScheme = new MavenVersionSelectorScheme(new DefaultVersionSelectorScheme())
    def result = Mock(PluginResolutionResult)

    def resolver = new ArtifactRepositoryPluginResolver("maven", null, versionSelectorScheme);

    ContextAwarePluginRequest request(String id, String version = null, String script = null) {
        new ContextAwarePluginRequest(
            new DefaultPluginRequest(new StringScriptSource("test", "test").displayName, 1, id, version, script, true),
            Mock(PluginRequestResolutionContext))
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
