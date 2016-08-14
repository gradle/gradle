/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.memcache

import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.internal.component.ArtifactType
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier
import org.gradle.internal.component.model.ComponentArtifactMetadata
import org.gradle.internal.component.model.ComponentArtifacts
import org.gradle.internal.resolve.result.BuildableArtifactResolveResult
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult
import org.gradle.internal.resolve.result.BuildableComponentArtifactsResolveResult
import spock.lang.Specification

class InMemoryArtifactsCacheTest extends Specification {
    def cache = new InMemoryArtifactsCache()

    static componentId(String group, String module, String version) {
        return DefaultModuleComponentIdentifier.newId(group, module, version)
    }

    def "caches and supplies artifacts"() {
        def artifactId = Stub(ModuleComponentArtifactIdentifier)
        def artifactFile = new File("foo")

        given:
        def originalResult = Stub(BuildableArtifactResolveResult)
        originalResult.successful >> true
        originalResult.result >> artifactFile

        cache.newArtifact(artifactId, originalResult)

        def result = Mock(BuildableArtifactResolveResult)
        def differentId = Stub(ModuleComponentArtifactIdentifier)

        when:
        def differentIdFound = cache.supplyArtifact(differentId, result)

        then:
        !differentIdFound
        0 * result._

        when:
        def sameIdFound = cache.supplyArtifact(artifactId, result)

        then:
        sameIdFound
        1 * result.resolved(artifactFile)
    }

    def "does not cache failed artifact resolves"() {
        def artifactId = Stub(ModuleComponentArtifactIdentifier)
        def failedResult = Stub(BuildableArtifactResolveResult)
        def result = Mock(BuildableArtifactResolveResult)

        given:
        cache.newArtifact(artifactId, failedResult)

        when:
        def fromCache = cache.supplyArtifact(artifactId, result)

        then:
        !fromCache
        0 * result._
    }

    def "caches and supplies component artifacts"() {
        def artifacts = Stub(ComponentArtifacts)
        def component = Stub(ComponentIdentifier)

        given:
        def originalResult = Stub(BuildableComponentArtifactsResolveResult)
        originalResult.successful >> true
        originalResult.result >> artifacts

        cache.newArtifacts(component, originalResult)

        def result = Mock(BuildableComponentArtifactsResolveResult)
        def differentId = Stub(ComponentIdentifier)

        when:
        def differentIdFound = cache.supplyArtifacts(differentId, result)

        then:
        !differentIdFound
        0 * result._

        when:
        def sameIdFound = cache.supplyArtifacts(component, result)

        then:
        sameIdFound
        1 * result.resolved(artifacts)
    }

    def "does not cache failed component artifact resolves"() {
        def component = Stub(ComponentIdentifier)
        def failedResult = Stub(BuildableComponentArtifactsResolveResult)
        def result = Mock(BuildableComponentArtifactsResolveResult)

        given:
        cache.newArtifacts(component, failedResult)

        when:
        def fromCache = cache.supplyArtifacts(component, result)

        then:
        !fromCache
        0 * result._
    }

    def "caches and supplies component typed artifacts"() {
        def artifacts = [Stub(ComponentArtifactMetadata)] as Set
        def component = Stub(ComponentIdentifier)

        given:
        def originalResult = Stub(BuildableArtifactSetResolveResult)
        originalResult.successful >> true
        originalResult.result >> artifacts

        cache.newArtifacts(component, ArtifactType.JAVADOC, originalResult)

        def result = Mock(BuildableArtifactSetResolveResult)
        def differentId = Stub(ComponentIdentifier)

        when:
        def differentIdFound = cache.supplyArtifacts(differentId, ArtifactType.JAVADOC, result)

        then:
        !differentIdFound
        0 * result._


        when:
        def differentTypeFound = cache.supplyArtifacts(component, ArtifactType.SOURCES, result)

        then:
        !differentTypeFound
        0 * result._

        when:
        def sameIdFound = cache.supplyArtifacts(component, ArtifactType.JAVADOC, result)

        then:
        sameIdFound
        1 * result.resolved(artifacts)
    }

    def "does not cache failed component typed artifact resolves"() {
        def component = Stub(ComponentIdentifier)
        def failedResult = Stub(BuildableArtifactSetResolveResult)
        def result = Mock(BuildableArtifactSetResolveResult)

        given:
        cache.newArtifacts(component, ArtifactType.JAVADOC, failedResult)

        when:
        def fromCache = cache.supplyArtifacts(component, ArtifactType.JAVADOC, result)

        then:
        !fromCache
        0 * result._
    }
}
