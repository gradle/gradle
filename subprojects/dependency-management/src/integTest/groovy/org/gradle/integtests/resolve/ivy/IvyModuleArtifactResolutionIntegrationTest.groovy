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

package org.gradle.integtests.resolve.ivy

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.resolve.MetadataArtifactResolveTestFixture
import org.gradle.internal.resolve.ArtifactResolveException
import org.gradle.test.fixtures.ivy.IvyRepository
import org.gradle.test.fixtures.server.http.IvyHttpModule

class IvyModuleArtifactResolutionIntegrationTest extends AbstractHttpDependencyResolutionTest {
    private MetadataArtifactResolveTestFixture fixture
    def httpRepo = ivyHttpRepo

    def setup() {
        initBuild(httpRepo)
        fixture = new MetadataArtifactResolveTestFixture(buildFile)
        fixture.basicSetup()
    }

    def initBuild(IvyRepository repo) {
        buildFile << """
repositories {
    ivy { url '$repo.uri' }
}
"""
    }

    @ToBeFixedForConfigurationCache
    def "successfully resolve existing Ivy module artifact"() {
        given:
        IvyHttpModule module = publishModule()

        when:
        fixture.requestComponent('IvyModule').requestArtifact('IvyDescriptorArtifact')
               .expectResolvedComponentResult().expectMetadataFiles(module.ivy.file)
               .createVerifyTaskModuleComponentIdentifier()

        module.ivy.expectGet()

        then:
        checkArtifactsResolvedAndCached()
    }

    @ToBeFixedForConfigurationCache
    def "invalid component type and artifact type (#reason)"() {
        given:
        IvyHttpModule module = publishModule()

        when:
        fixture.requestComponent(component).requestArtifact(artifactType)
               .expectUnresolvedComponentResult(exception)
               .expectNoMetadataFiles()
               .createVerifyTaskModuleComponentIdentifier()
        module.ivy.expectGet()

        then:
        checkArtifactsResolvedAndCached()

        where:
        component     | artifactType       | reason                                                                    | exception
        'IvyModule'   | 'MavenPomArtifact' | 'cannot mix IvyModule with Maven metadata artifact type MavenPomArtifact' | new IllegalArgumentException('Artifact type org.gradle.maven.MavenPomArtifact is not registered for component type org.gradle.ivy.IvyModule.')
        'MavenModule' | 'MavenPomArtifact' | 'cannot retrieve Maven component and metadata artifact for Ivy module'    | new ArtifactResolveException("Could not determine artifacts for some.group:some-artifact:1.0: Cannot locate 'maven pom' artifacts for 'some.group:some-artifact:1.0' in repository 'ivy'")
    }

    @ToBeFixedForConfigurationCache
    def "requesting IvyModule for a project component"() {
        given:
        IvyHttpModule module = publishModule()

        when:
        fixture.requestComponent('IvyModule').requestArtifact('IvyDescriptorArtifact')
               .expectUnresolvedComponentResult(new IllegalArgumentException("Cannot query artifacts for a project component (project :)."))
               .expectNoMetadataFiles()
               .createVerifyTaskForProjectComponentIdentifier()

        module.ivy.expectGet()

        then:
        checkArtifactsResolvedAndCached()
    }

    @ToBeFixedForConfigurationCache
    def "request an ivy descriptor for an ivy module with no descriptor when artifact metadata source are configured"() {
        given:
        IvyHttpModule module = publishModuleWithoutMetadata()
        buildFile << """
            repositories.all {
                metadataSources {
                    ivyDescriptor()
                    artifact()
                }
            }
        """

        when:
        fixture.requestComponent('IvyModule').requestArtifact('IvyDescriptorArtifact')
               .expectResolvedComponentResult()
               .expectNoMetadataFiles()
               .expectUnresolvedArtifactResult(ArtifactResolveException, "Could not find ivy-1.0.xml (some.group:some-artifact:1.0).")
               .createVerifyTaskModuleComponentIdentifier()

        // TODO - should do single request
        module.ivy.expectGetMissing()
        module.ivy.expectGetMissing()
        module.jar.expectHead()

        then:
        checkArtifactsResolvedAndCached()
    }

    @ToBeFixedForConfigurationCache
    def "request an ivy descriptor for an ivy module with a custom ivy pattern"() {
        given:
        httpRepo = server.getRemoteIvyRepo(true, "[module]/[revision]", "alternate-ivy.xml", "[artifact](.[ext])")

        buildFile.text = """
repositories {
    ivy {
        url '${httpRepo.uri}'
        patternLayout {
            artifact '[module]/[revision]/[artifact](.[ext])'
            ivy '[module]/[revision]/alternate-ivy.xml'
        }
    }
}
"""
        fixture = new MetadataArtifactResolveTestFixture(buildFile)
        fixture.basicSetup()
        IvyHttpModule module = publishModule()

        when:
        fixture.requestComponent('IvyModule').requestArtifact('IvyDescriptorArtifact')
                .expectResolvedComponentResult().expectMetadataFiles(file("ivy-${fixture.id.version}.xml"))
                .createVerifyTaskModuleComponentIdentifier()

        module.ivy.expectGet()

        then:
        checkArtifactsResolvedAndCached()
    }

    @ToBeFixedForConfigurationCache
    def "updates artifacts for module #condition"() {
        given:
        IvyHttpModule module = publishModule()

        fixture.configureChangingModule()
        fixture.requestComponent('IvyModule').requestArtifact('IvyDescriptorArtifact')
               .expectResolvedComponentResult().expectMetadataFiles(module.ivyFile)
               .createVerifyTaskModuleComponentIdentifier()

        when:
        module.ivy.expectGet()

        then:
        checkArtifactsResolvedAndCached()

        when:
        module.publishWithChangedContent()

        and:
        server.resetExpectations()
        module.ivy.expectHead()
        module.ivy.sha1.expectGet()
        module.ivy.expectGet()

        then:
        executer.withArgument(execArg)
        succeeds("verify")

        where:
        condition                     | execArg
        "with --refresh-dependencies" | "--refresh-dependencies"
        "when ivy descriptor changes" | "-Pnocache"
    }

    private IvyHttpModule publishModule() {
        def module = createModule()
        module.publish()
    }

    private IvyHttpModule publishModuleWithoutMetadata() {
        IvyHttpModule module = createModule()
        module.withNoMetaData()
        module.publish()
    }

    private IvyHttpModule createModule() {
        httpRepo.module(fixture.id.group, fixture.id.module, fixture.id.version)
    }

    def checkArtifactsResolvedAndCached() {
        assert succeeds('verify')
        server.resetExpectations()
        assert succeeds('verify')
        true
    }
}
