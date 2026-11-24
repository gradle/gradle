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

package org.gradle.api.internal.artifacts.repositories.descriptor

import spock.lang.Specification

class RepositoryDescriptorUrlCredentialsTest extends Specification {

    def url = new URI("https://someuser:s3cret@repo.example.com/repo")
    def extraUrl = new URI("https://someuser:s3cret@repo.example.com/extra")

    def "credentials embedded in the URL of a Maven repository stay part of its id"() {
        when:
        def descriptor = maven(url, extraUrl)

        then:
        // The id identifies the repository in the dependency cache, it has to stay the same across Gradle versions
        descriptor.id == "9b38b6675ef7625e64f73848723d5329"
        descriptor.id != maven(new URI("https://repo.example.com/repo"), new URI("https://repo.example.com/extra")).id

        and:
        descriptor.properties.URL == new URI("https://***@repo.example.com/repo")
        descriptor.properties.ARTIFACT_URLS == [new URI("https://***@repo.example.com/extra")]
    }

    def "credentials embedded in the URL of an Ivy repository stay part of its id"() {
        when:
        def descriptor = ivy(url, extraUrl)

        then:
        // The id identifies the repository in the dependency cache, it has to stay the same across Gradle versions
        descriptor.id == "0a7de3352cf110c7eedc3ecd49207252"
        descriptor.id != ivy(new URI("https://repo.example.com/repo"), new URI("https://repo.example.com/extra")).id

        and:
        descriptor.properties.URL == new URI("https://***@repo.example.com/repo")
        descriptor.properties.IVY_PATTERNS == ["https://***@repo.example.com/extra/[module]/ivy.xml"]
        descriptor.properties.ARTIFACT_PATTERNS == ["https://***@repo.example.com/extra/[module]/[artifact].[ext]"]
    }

    private static MavenRepositoryDescriptor maven(URI url, URI artifactUrl) {
        new MavenRepositoryDescriptor.Builder("repo", url)
            .setArtifactUrls([artifactUrl])
            .setMetadataSources(["mavenPom"])
            .setAuthenticated(false)
            .setAuthenticationSchemes([])
            .create()
    }

    private static IvyRepositoryDescriptor ivy(URI url, URI extraUrl) {
        def builder = new IvyRepositoryDescriptor.Builder("repo", url)
            .setLayoutType("Pattern")
            .setM2Compatible(false)
            .setMetadataSources(["ivyDescriptor"])
            .setAuthenticated(false)
            .setAuthenticationSchemes([])
        builder.addIvyPattern("${extraUrl}/[module]/ivy.xml")
        builder.addIvyResource(extraUrl, "[module]/ivy.xml")
        builder.addArtifactPattern("${extraUrl}/[module]/[artifact].[ext]")
        builder.addArtifactResource(extraUrl, "[module]/[artifact].[ext]")
        builder.addArtifactResource(url, "[module]/[artifact].[ext]")
        builder.create()
    }
}
