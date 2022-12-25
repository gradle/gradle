/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.integtests.resolve.maven

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.resolve.MetadataArtifactResolveTestFixture
import org.gradle.internal.resolve.ArtifactResolveException
import org.gradle.test.fixtures.maven.MavenRepository
import org.gradle.test.fixtures.server.http.MavenHttpModule

class MavenModuleArtifactResolutionIntegrationTest extends AbstractHttpDependencyResolutionTest {
    private MetadataArtifactResolveTestFixture fixture
    def httpRepo = mavenHttpRepo

    def setup() {
        initBuild(httpRepo)
        fixture = new MetadataArtifactResolveTestFixture(buildFile)
        fixture.basicSetup()
    }

    def initBuild(MavenRepository repo) {
        buildFile << """
repositories {
    maven { url '$repo.uri' }
}
"""
    }

    @ToBeFixedForConfigurationCache
    def "successfully resolve existing Maven module artifact"() {
        given:
        MavenHttpModule module = publishModule()

        when:
        fixture.requestComponent('MavenModule').requestArtifact('MavenPomArtifact')
            .expectResolvedComponentResult().expectMetadataFiles(module.pom.file)
            .createVerifyTaskModuleComponentIdentifier()

        module.pom.expectGet()

        then:
        checkArtifactsResolvedAndCached()
    }

    @ToBeFixedForConfigurationCache
    def "invalid component type and artifact type (#reason)"() {
        given:
        MavenHttpModule module = publishModule()

        when:
        fixture.requestComponent(component).requestArtifact(artifactType)
            .expectUnresolvedComponentResult(exception)
            .expectNoMetadataFiles()
            .createVerifyTaskModuleComponentIdentifier()
        module.pom.expectGet()

        then:
        checkArtifactsResolvedAndCached()

        where:
        component     | artifactType            | reason                                                                           | exception
        'MavenModule' | 'IvyDescriptorArtifact' | 'cannot mix MavenModule with Maven metadata artifact type IvyDescriptorArtifact' | new IllegalArgumentException('Artifact type org.gradle.ivy.IvyDescriptorArtifact is not registered for component type org.gradle.maven.MavenModule.')
        'IvyModule'   | 'IvyDescriptorArtifact' | 'cannot retrieve Ivy component and metadata artifact for Maven module'           | new ArtifactResolveException("Could not determine artifacts for some.group:some-artifact:1.0: Cannot locate 'ivy descriptor' artifacts for 'some.group:some-artifact:1.0' in repository 'maven'")
    }

    @ToBeFixedForConfigurationCache
    def "requesting MavenModule for a project component"() {
        MavenHttpModule module = publishModule()

        when:
        fixture.requestComponent('MavenModule').requestArtifact('MavenPomArtifact')
            .expectUnresolvedComponentResult(new IllegalArgumentException("Cannot query artifacts for a project component (project :)."))
            .expectNoMetadataFiles()
            .createVerifyTaskForProjectComponentIdentifier()

        module.pom.expectGet()

        then:
        checkArtifactsResolvedAndCached()
    }

    @ToBeFixedForConfigurationCache
    def "request an Maven POM for a Maven module with no metadata when artifact metadata source are configured"() {
        given:
        MavenHttpModule module = publishModuleWithoutMetadata()
        buildFile << """
            repositories.all {
                metadataSources {
                    mavenPom()
                    artifact()
                }
            }
        """

        when:
        fixture.requestComponent('MavenModule').requestArtifact('MavenPomArtifact')
            .expectResolvedComponentResult()
            .expectNoMetadataFiles()
            .expectUnresolvedArtifactResult(ArtifactResolveException, "Could not find some-artifact-1.0.pom (some.group:some-artifact:1.0).")
            .createVerifyTaskModuleComponentIdentifier()

        // TODO - should make a single request
        module.pom.expectGetMissing()
        module.pom.expectGetMissing()
        module.artifact.expectHead()

        then:
        checkArtifactsResolvedAndCached()
    }

    @ToBeFixedForConfigurationCache
    def "updates artifacts for module #condition"() {
        given:
        MavenHttpModule module = publishModule()

        fixture.configureChangingModule()
        fixture.requestComponent('MavenModule').requestArtifact('MavenPomArtifact')
            .expectResolvedComponentResult().expectMetadataFiles(module.pomFile)
            .createVerifyTaskModuleComponentIdentifier()

        when:
        module.pom.expectGet()

        then:
        checkArtifactsResolvedAndCached()

        when:
        module.publishWithChangedContent()

        and:
        server.resetExpectations()
        module.pom.expectHead()
        module.pom.sha1.expectGet()
        module.pom.expectGet()

        then:
        executer.withArgument(execArg)
        succeeds("verify")

        where:
        condition                     | execArg
        "with --refresh-dependencies" | "--refresh-dependencies"
        "when maven pom changes"      | "-Pnocache"
    }

    private MavenHttpModule publishModule() {
        def module = createModule()
        module.publish()
    }

    private MavenHttpModule publishModuleWithoutMetadata() {
        MavenHttpModule module = createModule()
        module.withNoPom()
        module.publish()
    }

    private MavenHttpModule createModule() {
        httpRepo.module(fixture.id.group, fixture.id.module, fixture.id.version)
    }

    def checkArtifactsResolvedAndCached() {
        assert succeeds('verify')
        server.resetExpectations()
        assert succeeds('verify')
        true
    }
}
