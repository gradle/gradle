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

import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionComparator
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionSelectorScheme
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.MavenVersionSelectorScheme
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser
import spock.lang.Specification

import static org.gradle.plugin.use.resolve.internal.ArtifactRepositoriesPluginResolver.SOURCE_NAME

class ArtifactRepositoriesPluginResolverTest extends Specification {

    def versionSelectorScheme = new MavenVersionSelectorScheme(new DefaultVersionSelectorScheme(new DefaultVersionComparator(), new VersionParser()))
    def result = Mock(PluginResolutionResult)

    def "validation of pluginRequests without version fails"() {
        when:
        ArtifactRepositoriesPluginResolver.validateVersion(versionSelectorScheme, null, result)

        then:
        1 * result.notFound(SOURCE_NAME, "plugin dependency must include a version number for this source")
    }

    def "validation of pluginRequests with SNAPSHOT versions succeeds"() {
        when:
        ArtifactRepositoriesPluginResolver.validateVersion(versionSelectorScheme, "1.1-SNAPSHOT", result)

        then:
        0 * result.notFound(SOURCE_NAME, _)
    }

    def "validation of pluginRequests with dynamic versions fails"() {
        when:
        ArtifactRepositoriesPluginResolver.validateVersion(versionSelectorScheme, "latest.revision", result)

        then:
        1 * result.notFound(SOURCE_NAME, "dynamic plugin versions are not supported")
    }
}
