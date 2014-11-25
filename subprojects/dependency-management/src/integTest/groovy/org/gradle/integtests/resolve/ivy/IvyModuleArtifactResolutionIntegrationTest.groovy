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

import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.resolve.MetadataArtifactResolveTestFixture
import org.gradle.test.fixtures.server.http.IvyHttpModule

class IvyModuleArtifactResolutionIntegrationTest extends AbstractHttpDependencyResolutionTest {
    private MetadataArtifactResolveTestFixture metadataArtifactResolveTestFixture
    private ModuleComponentIdentifier id
    def httpRepo = ivyHttpRepo

    def setup() {
        metadataArtifactResolveTestFixture = new MetadataArtifactResolveTestFixture(buildFile, 'conf')
        id = metadataArtifactResolveTestFixture.id
        metadataArtifactResolveTestFixture.prepare()

        buildFile << """
repositories {
    ivy { url '$httpRepo.uri' }
}
"""
    }

    def "sucessfully resolve existing Ivy module artifact"() {
        given:
        IvyHttpModule module = publishModule()

        when:
        buildFile << """
task verify {
    doLast {
        def deps = configurations.conf.incoming.resolutionResult.allDependencies as List
        assert deps.size() == 1
        def componentId = deps[0].selected.id

        def result = dependencies.createArtifactResolutionQuery()
            .forComponents(deps[0].selected.id)
            .withArtifacts(IvyModule, IvyDescriptorArtifact)
            .execute()

        assert result.components.size() == 1

        // Check generic component result
        def componentResult = result.components.iterator().next()
        assert componentResult.id.displayName == '$id.displayName'
        assert componentResult instanceof ComponentArtifactsResult

        Set<File> ivyFiles = result.artifactFiles
        assert ivyFiles.size() == 1
    }
}
"""
        module.ivy.expectGet()

        then:
        checkArtifactsResolvedAndCached()
    }

    def "fails to resolve Maven module artifact"() {
        given:
        IvyHttpModule module = publishModule()

        when:
        buildFile << """
task verify {
    doLast {
        def deps = configurations.conf.incoming.resolutionResult.allDependencies as List
        assert deps.size() == 1
        def componentId = deps[0].selected.id

        def result = dependencies.createArtifactResolutionQuery()
            .forComponents(deps[0].selected.id)
            .withArtifacts(MavenModule, MavenPomArtifact)
            .execute()

        assert result.components.size() == 1

        // Check generic component result
        def componentResult = result.components.iterator().next()
        assert componentResult.id.displayName == '$id.displayName'
        assert componentResult instanceof UnresolvedComponentResult

        Set<File> ivyFiles = result.artifactFiles
        assert ivyFiles.size() == 0
    }
}
"""
        module.ivy.expectGet()

        then:
        checkArtifactsResolvedAndCached()
    }

    private IvyHttpModule publishModule() {
        def module = httpRepo.module(id.group, id.module, id.version)
        module.publish()
    }

    def checkArtifactsResolvedAndCached() {
        assert succeeds('verify')
        server.resetExpectations()
        assert succeeds('verify')
        true
    }
}
