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
import org.gradle.integtests.fixtures.executer.ExecutionFailure
import org.gradle.integtests.resolve.MetadataArtifactResolveTestFixture
import org.gradle.test.fixtures.maven.MavenRepository
import org.gradle.test.fixtures.server.http.MavenHttpModule
import spock.lang.Unroll

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

    def "sucessfully resolve existing Maven module artifact"() {
        given:
        MavenHttpModule module = publishModule()

        when:
        fixture.requestComponent('MavenModule').requestArtifact('MavenPomArtifact')
               .expectComponentResult('ComponentArtifactsResult').expectMetadataFiles([module.pom.file] as Set)
               .createVerifyTaskModuleComponentIdentifier()

        module.pom.expectGet()

        then:
        checkArtifactsResolvedAndCached()
    }

    def "cannot call withArtifacts multiple times for query"() {
        given:
        MavenHttpModule module = publishModule()

        when:
        fixture.requestComponent('MavenModule').requestArtifact('MavenPomArtifact')
               .createVerifyTaskForDuplicateCallToWithArtifacts()

        module.pom.expectGet()
        ExecutionFailure failure = fails('verify')

        then:
        failure.assertHasCause('Cannot specify component type multiple times.')
    }

    @Unroll
    def "invalid component type and artifact type (#reason)"() {
        given:
        MavenHttpModule module = publishModule()

        when:
        fixture.requestComponent(component).requestArtifact(artifactType)
                .expectComponentResult('UnresolvedComponentResult').expectMetadataFiles([] as Set)
                .createVerifyTaskModuleComponentIdentifier()
        module.pom.expectGet()

        then:
        checkArtifactsResolvedAndCached()

        where:
        component     | artifactType            | reason
        'JvmLibrary'  | 'MavenPomArtifact'      | 'cannot mix JvmLibrary with metadata artifact types'
        'MavenModule' | 'SourcesArtifact'       | 'cannot mix MavenModule with JVM library artifact type SourcesArtifact'
        'MavenModule' | 'JavadocArtifact'       | 'cannot mix MavenModule with JVM library artifact type JavadocArtifact'
        'MavenModule' | 'IvyDescriptorArtifact' | 'cannot mix MavenModule with Maven metadata artifact type IvyDescriptorArtifact'
        'IvyModule'   | 'MavenPomArtifact'      | 'cannot retrieve Ivy component and metadata artifact for Maven module'
    }

    def "requesting MavenModule for a project component"() {
        given:
        MavenHttpModule module = publishModule()

        when:
        fixture.requestComponent('MavenModule').requestArtifact('MavenPomArtifact')
               .createVerifyTaskForProjectComponentIdentifier()

        module.pom.expectGet()
        ExecutionFailure failure = fails('verify')

        then:
        failure.assertHasCause("Cannot resolve the artifacts for component project : with unsupported type org.gradle.internal.component.local.model.DefaultProjectComponentIdentifier.")
    }

    def "request an Maven POM for a Maven module with no metadata"() {
        given:
        MavenHttpModule module = publishModuleWithoutMetadata()

        when:
        fixture.requestComponent('MavenModule').requestArtifact('MavenPomArtifact')
               .expectComponentResult('ComponentArtifactsResult').expectMetadataFiles([] as Set)
               .createVerifyTaskModuleComponentIdentifier()

        // TODO: Need to look into expectations
        module.pom.expectGet()
        module.pom.expectGet()
        module.artifact.expectHead()
        ExecutionFailure failure = fails('verify')

        then:
        failure.assertHasCause("""Could not find some-artifact.pom (${fixture.id.displayName}).
Searched in the following locations:
    ${module.pom.uri}""")

    }

    @Unroll
    def "updates artifacts for module #condition"() {
        given:
        MavenHttpModule module = publishModule()

        fixture.configureChangingModule()
        fixture.requestComponent('MavenModule').requestArtifact('MavenPomArtifact')
               .expectComponentResult('ComponentArtifactsResult').expectMetadataFiles([module.pomFile] as Set)
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
        module.withNoMetaData()
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
