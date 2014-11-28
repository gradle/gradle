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
import org.gradle.integtests.fixtures.executer.ExecutionFailure
import org.gradle.integtests.resolve.MetadataArtifactResolveTestFixture
import org.gradle.test.fixtures.ivy.IvyRepository
import org.gradle.test.fixtures.server.http.IvyHttpModule
import spock.lang.Unroll

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

    def "sucessfully resolve existing Ivy module artifact"() {
        given:
        IvyHttpModule module = publishModule()

        when:
        fixture.requestComponent('IvyModule').requestArtifact('IvyDescriptorArtifact')
               .expectComponentResult('ComponentArtifactsResult').expectMetadataFiles([module.ivy.file] as Set)
               .createVerifyTaskModuleComponentIdentifier()

        module.ivy.expectGet()

        then:
        checkArtifactsResolvedAndCached()
    }

    def "cannot call withArtifacts multiple times for query"() {
        given:
        IvyHttpModule module = publishModule()

        when:
        fixture.requestComponent('IvyModule').requestArtifact('IvyDescriptorArtifact')
               .createVerifyTaskForDuplicateCallToWithArtifacts()

        module.ivy.expectGet()
        ExecutionFailure failure = fails('verify')

        then:
        failure.assertHasCause('Cannot specify component type multiple times.')
    }

    @Unroll
    def "invalid component type and artifact type (#reason)"() {
        given:
        IvyHttpModule module = publishModule()

        when:
        fixture.requestComponent(component).requestArtifact(artifactType)
               .expectComponentResult('UnresolvedComponentResult').expectMetadataFiles([] as Set)
               .createVerifyTaskModuleComponentIdentifier()
        module.ivy.expectGet()

        then:
        checkArtifactsResolvedAndCached()

        where:
        component     | artifactType            | reason
        'JvmLibrary'  | 'IvyDescriptorArtifact' | 'cannot mix JvmLibrary with metadata artifact types'
        'IvyModule'   | 'SourcesArtifact'       | 'cannot mix IvyModule with JVM library artifact type SourcesArtifact'
        'IvyModule'   | 'JavadocArtifact'       | 'cannot mix IvyModule with JVM library artifact type JavadocArtifact'
        'IvyModule'   | 'MavenPomArtifact'      | 'cannot mix IvyModule with Maven metadata artifact type MavenPomArtifact'
        'MavenModule' | 'MavenPomArtifact'      | 'cannot retrieve Maven component and metadata artifact for Ivy module'
    }

    def "requesting IvyModule for a project component"() {
        given:
        IvyHttpModule module = publishModule()

        when:
        fixture.requestComponent('IvyModule').requestArtifact('IvyDescriptorArtifact')
               .createVerifyTaskForProjectComponentIdentifier()

        module.ivy.expectGet()
        ExecutionFailure failure = fails('verify')

        then:
        failure.assertHasCause("Cannot resolve the artifacts for component project : with unsupported type org.gradle.internal.component.local.model.DefaultProjectComponentIdentifier.")
    }

    def "request an ivy descriptor for an ivy module with no descriptor"() {
        given:
        IvyHttpModule module = publishModuleWithoutMetadata()

        when:
        fixture.requestComponent('IvyModule').requestArtifact('IvyDescriptorArtifact')
               .expectComponentResult('ComponentArtifactsResult').expectMetadataFiles([] as Set)
               .createVerifyTaskModuleComponentIdentifier()

        // TODO: Need to look into expectations
        module.ivy.expectGet()
        module.ivy.expectGet()
        module.jar.expectHead()
        ExecutionFailure failure = fails('verify')

        then:
        failure.assertHasCause("Artifact 'ivy.xml (${fixture.id.displayName})' not found.")
    }

    @Unroll
    def "updates artifacts for module #condition"() {
        given:
        IvyHttpModule module = publishModule()

        fixture.configureChangingModule()
        fixture.requestComponent('IvyModule').requestArtifact('IvyDescriptorArtifact')
               .expectComponentResult('ComponentArtifactsResult').expectMetadataFiles([module.ivyFile] as Set)
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
