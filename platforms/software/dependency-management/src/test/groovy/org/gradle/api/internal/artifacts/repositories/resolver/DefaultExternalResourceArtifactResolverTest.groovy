/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.artifacts.repositories.resolver

import org.gradle.api.InvalidUserDataException
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.external.model.UrlBackedArtifactMetadata
import org.gradle.internal.resolve.result.ResourceAwareResolveResult
import org.gradle.internal.resource.ExternalResourceName
import org.gradle.internal.resource.ExternalResourceRepository
import org.gradle.internal.resource.local.FileStore
import org.gradle.internal.resource.local.LocallyAvailableExternalResource
import org.gradle.internal.resource.local.LocallyAvailableResourceFinder
import org.gradle.internal.resource.transfer.CacheAwareExternalResourceAccessor
import spock.lang.Specification

class DefaultExternalResourceArtifactResolverTest extends Specification {
    def flat = new IvyResourcePattern(new URI("http://host/flat"), "[module]-[revision]/[artifact]-[revision].[ext]")
    def deep = new IvyResourcePattern(new URI("http://host/deep"), "[organisation]/[module]/[revision]/[artifact]-[revision].[ext]")
    def resourceAccessor = Mock(CacheAwareExternalResourceAccessor)
    def result = Mock(ResourceAwareResolveResult)
    def resolver = new DefaultExternalResourceArtifactResolver(Mock(ExternalResourceRepository), Mock(LocallyAvailableResourceFinder), [], [flat, deep], Mock(FileStore), resourceAccessor)
    def componentId = DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("group", "module"), "1.0")

    def "skips the patterns for which the url of a file designates a location outside the repository"() {
        def resource = Mock(LocallyAvailableExternalResource)
        def artifact = new UrlBackedArtifactMetadata(componentId, "file.jar", "../../shared/file.jar")

        when:
        def resolved = resolver.resolveArtifact(artifact, result)

        then:
        resolved == resource
        1 * resourceAccessor.getResource({ ExternalResourceName name -> name.uri == new URI("http://host/deep/group/shared/file.jar") }, _, _, _) >> resource
        0 * resourceAccessor._
    }

    def "fails when the url '#url' of a file designates a location outside the repository for all patterns"() {
        def artifact = new UrlBackedArtifactMetadata(componentId, "file.jar", url)

        when:
        resolver.resolveArtifact(artifact, result)

        then:
        def e = thrown(InvalidUserDataException)
        e.message == "Cannot locate file 'file.jar' of group:module:1.0 as its URL '${url}' designates a location outside the repository."
        0 * resourceAccessor._

        where:
        url << [
            "../../../../shared/file.jar",
            "../../../../../file.jar",
            "/file.jar",
            "..;/..;/..;/..;/shared/file.jar",
            "..;a=b/..;/..;c/..;/shared/file.jar",
            "sub/..;/..;/..;/..;/..;/file.jar"
        ]
    }

    def "accepts the url '#url' with path parameters that designates a location in the repository"() {
        def resource = Mock(LocallyAvailableExternalResource)
        def artifact = new UrlBackedArtifactMetadata(componentId, "file.jar", url)

        when:
        def resolved = resolver.resolveArtifact(artifact, result)

        then:
        resolved == resource
        1 * resourceAccessor.getResource({ ExternalResourceName name -> name.uri == new URI(expectedLocation) }, _, _, _) >> resource

        where:
        url                    | expectedLocation
        "file;v=1.jar"         | "http://host/flat/module-1.0/file;v=1.jar"
        "sub;a=b/file.jar"     | "http://host/flat/module-1.0/sub;a=b/file.jar"
        "..;/module-2/file.jar" | "http://host/flat/module-1.0/..;/module-2/file.jar"
    }
}
