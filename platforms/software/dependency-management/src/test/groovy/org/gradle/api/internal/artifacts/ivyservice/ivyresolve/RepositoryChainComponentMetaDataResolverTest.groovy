/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve


import org.gradle.api.artifacts.ModuleVersionSelector
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.external.model.ModuleComponentGraphResolveState
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata
import org.gradle.internal.component.model.ComponentGraphResolveState
import org.gradle.internal.component.model.ComponentGraphSpecificResolveState
import org.gradle.internal.component.model.ComponentOverrideMetadata
import org.gradle.internal.model.CalculatedValueContainerFactory
import org.gradle.internal.resolve.ModuleVersionNotFoundException
import org.gradle.internal.resolve.ModuleVersionResolveException
import org.gradle.internal.resolve.result.BuildableComponentResolveResult
import org.gradle.internal.resources.ProjectLeaseRegistry
import org.gradle.internal.service.ServiceRegistry
import spock.lang.Specification

class RepositoryChainComponentMetaDataResolverTest extends Specification {
    final org.gradle.internal.Factory<String> broken = { "broken" }
    final metaData = metaData("1.2")
    final componentState = Stub(ModuleComponentGraphResolveState) {
        getLegacyMetadata() >> metaData
    }
    final moduleComponentId = DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("group", "project"), "1.0")
    final componentRequestMetaData = Mock(ComponentOverrideMetadata)

    final result = Mock(BuildableComponentResolveResult)
    def localAccess = Mock(ModuleComponentRepositoryAccess)
    def remoteAccess = Mock(ModuleComponentRepositoryAccess)
    def localAccess2 = Mock(ModuleComponentRepositoryAccess)
    def remoteAccess2 = Mock(ModuleComponentRepositoryAccess)

    final VersionedComponentChooser componentSelectionStrategy = Mock(VersionedComponentChooser)
    def calculatedValueContainerFactory = new CalculatedValueContainerFactory(Mock(ProjectLeaseRegistry), Mock(ServiceRegistry))
    final RepositoryChainComponentMetaDataResolver resolver = new RepositoryChainComponentMetaDataResolver(componentSelectionStrategy, calculatedValueContainerFactory)

    def addRepo1() {
        addModuleComponentRepository("repo1", localAccess, remoteAccess)
    }

    def addRepo2() {
        addModuleComponentRepository("repo2", localAccess2, remoteAccess2)
    }

    def addModuleComponentRepository(def name, def repoLocalAccess, def repoRemoteAccess) {
        def repo = Stub(ModuleComponentRepository) {
            getLocalAccess() >> repoLocalAccess
            getRemoteAccess() >> repoRemoteAccess
            getName() >> name
        }
        resolver.add(repo)
        repo
    }

    def "uses local dependency when available"() {
        given:
        def repo = addRepo1()

        when:
        resolver.resolve(moduleComponentId, componentRequestMetaData, result)

        then:
        1 * localAccess.resolveComponentMetaData(moduleComponentId, componentRequestMetaData, _) >> { id, meta, result ->
            result.resolved(componentState)
        }
        1 * result.resolved(_, _) >> { ComponentGraphResolveState state, ComponentGraphSpecificResolveState graphState ->
            assert state == componentState
            assert graphState.repositoryName == repo.name
        }

        and:
        0 * localAccess._
        0 * remoteAccess._
        0 * result._
    }

    def "attempts to find remote dependency when local dependency is unknown"() {
        given:
        def repo = addRepo1()

        when:
        resolver.resolve(moduleComponentId, componentRequestMetaData, result)

        then:
        1 * localAccess.resolveComponentMetaData(moduleComponentId, componentRequestMetaData, _)
        1 * remoteAccess.resolveComponentMetaData(moduleComponentId, componentRequestMetaData, _) >> { id, meta, result ->
            result.resolved(componentState)
        }
        1 * result.resolved(_, _) >> { ComponentGraphResolveState state, ComponentGraphSpecificResolveState graphState ->
            assert state == componentState
            assert graphState.repositoryName == repo.name
        }

        and:
        0 * localAccess._
        0 * remoteAccess._
        0 * result._
    }

    def "attempts to find remote dependency when local dependency is probably missing"() {
        given:
        def repo = addRepo1()

        when:
        resolver.resolve(moduleComponentId, componentRequestMetaData, result)

        then:
        1 * localAccess.resolveComponentMetaData(moduleComponentId, componentRequestMetaData, _) >> { id, meta, result ->
            result.missing()
            result.authoritative = false
        }
        1 * remoteAccess.resolveComponentMetaData(moduleComponentId, componentRequestMetaData, _) >> { id, meta, result ->
            result.resolved(componentState)
        }
        1 * result.resolved(_, _) >> { ComponentGraphResolveState state, ComponentGraphSpecificResolveState graphState ->
            assert state == componentState
            assert graphState.repositoryName == repo.name
        }

        and:
        0 * localAccess._
        0 * remoteAccess._
        0 * result._
    }

    def "fails with not found when local static dependency is marked as missing"() {
        given:
        def repo = addRepo1()

        when:
        resolver.resolve(moduleComponentId, componentRequestMetaData, result)

        then:
        1 * localAccess.resolveComponentMetaData(moduleComponentId, componentRequestMetaData, _) >> { id, meta, result ->
            result.attempted("scheme:thing")
            result.missing()
        }
        1 * result.attempted("scheme:thing")
        1 * result.failed(_) >> { args -> assert args[0] instanceof ModuleVersionNotFoundException }

        and:
        0 * localAccess._
        0 * remoteAccess._
        0 * result._
    }

    def "fails with not found when local and remote static dependency marked as missing"() {
        given:
        addRepo1()

        when:
        resolver.resolve(moduleComponentId, componentRequestMetaData, result)

        then:
        1 * localAccess.resolveComponentMetaData(moduleComponentId, componentRequestMetaData, _) >> { id, meta, result ->
            result.missing()
            result.authoritative = false
        }
        1 * remoteAccess.resolveComponentMetaData(moduleComponentId, componentRequestMetaData, _) >> { id, meta, result ->
            result.missing()
        }
        1 * result.failed(_) >> { args -> assert args[0] instanceof ModuleVersionNotFoundException }

        and:
        0 * localAccess._
        0 * remoteAccess._
        0 * result._
    }

    def "stops on first available local dependency for static version"() {
        given:
        def repo1 = addRepo1()
        def repo2 = Mock(ModuleComponentRepository)
        resolver.add(repo2)
        def repo3 = Mock(ModuleComponentRepository)
        resolver.add(repo3)

        when:
        resolver.resolve(moduleComponentId, componentRequestMetaData, result)

        then:
        1 * localAccess.resolveComponentMetaData(moduleComponentId, componentRequestMetaData, _) >> { id, meta, result ->
            result.resolved(componentState)
        }
        1 * result.resolved(_, _) >> { ComponentGraphResolveState state, ComponentGraphSpecificResolveState graphState ->
            assert state == componentState
            assert graphState.repositoryName == repo1.name
        }

        and:
        0 * repo2._
        0 * repo3._
        0 * localAccess._
        0 * remoteAccess._
        0 * result._
    }

    def "uses local dependency when available in one repository and missing from all other repositories"() {
        given:
        def repo1 = addRepo1()
        def repo2 = addRepo2()

        when:
        resolver.resolve(moduleComponentId, componentRequestMetaData, result)

        then:
        1 * localAccess.resolveComponentMetaData(moduleComponentId, componentRequestMetaData, _) >> { id, meta, result ->
            result.missing()
        }
        1 * localAccess2.resolveComponentMetaData(moduleComponentId, componentRequestMetaData, _) >> { id, meta, result ->
            result.resolved(componentState)
        }
        1 * result.resolved(_, _) >> { ComponentGraphResolveState state, ComponentGraphSpecificResolveState graphState ->
            assert state == componentState
            assert graphState.repositoryName == repo2.name
        }

        and:
        0 * localAccess._
        0 * remoteAccess._
        0 * localAccess2._
        0 * remoteAccess2._
        0 * result._
    }

    def "uses local dependency when available in one repository and probably missing in all other repositories"() {
        given:
        def repo1 = addRepo1()
        def repo2 = addRepo2()

        when:
        resolver.resolve(moduleComponentId, componentRequestMetaData, result)

        then:
        1 * localAccess.resolveComponentMetaData(moduleComponentId, componentRequestMetaData, _) >> { id, meta, result ->
            result.missing()
            result.authoritative = false
        }
        1 * localAccess2.resolveComponentMetaData(moduleComponentId, componentRequestMetaData, _) >> { id, meta, result ->
            result.resolved(componentState)
        }
        1 * result.resolved(_, _) >> { ComponentGraphResolveState state, ComponentGraphSpecificResolveState graphState ->
            assert state == componentState
            assert graphState.repositoryName == repo2.name
        }

        and:
        0 * localAccess._
        0 * remoteAccess._
        0 * localAccess2._
        0 * remoteAccess2._
        0 * result._
    }

    def "uses remote dependency when local dependency is unknown for a given repository and probably missing in other repositories"() {
        given:
        def repo1 = addRepo1()
        def repo2 = addRepo2()

        when:
        resolver.resolve(moduleComponentId, componentRequestMetaData, result)

        then:
        1 * localAccess.resolveComponentMetaData(moduleComponentId, componentRequestMetaData, _) >> { id, meta, result ->
            result.missing()
            result.authoritative = false
        }
        1 * localAccess2.resolveComponentMetaData(moduleComponentId, componentRequestMetaData, _)
        1 * remoteAccess2.resolveComponentMetaData(moduleComponentId, componentRequestMetaData, _) >> { id, meta, result ->
            result.resolved(componentState)
        }
        1 * result.resolved(_, _) >> { ComponentGraphResolveState state, ComponentGraphSpecificResolveState graphState ->
            assert state == componentState
            assert graphState.repositoryName == repo2.name
        }

        and:
        0 * localAccess._
        0 * remoteAccess._
        0 * localAccess2._
        0 * remoteAccess2._
        0 * result._
    }

    def "attempts to find remote dependency when local dependency is probably missing in all repositories"() {
        given:
        def repo1 = addRepo1()
        def repo2 = addRepo2()

        when:
        resolver.resolve(moduleComponentId, componentRequestMetaData, result)

        then:
        1 * localAccess.resolveComponentMetaData(moduleComponentId, componentRequestMetaData, _) >> { id, meta, result ->
            result.missing()
            result.authoritative = false
        }
        1 * localAccess2.resolveComponentMetaData(moduleComponentId, componentRequestMetaData, _) >> { id, meta, result ->
            result.missing()
            result.authoritative = false
        }
        1 * remoteAccess.resolveComponentMetaData(moduleComponentId, componentRequestMetaData, _) >> { id, meta, result ->
            result.missing()
        }
        1 * remoteAccess2.resolveComponentMetaData(moduleComponentId, componentRequestMetaData, _) >> { id, meta, result ->
            result.resolved(componentState)
        }
        1 * result.resolved(_, _) >> { ComponentGraphResolveState state, ComponentGraphSpecificResolveState graphState ->
            assert state == componentState
            assert graphState.repositoryName == repo2.name
        }

        and:
        0 * localAccess._
        0 * remoteAccess._
        0 * localAccess2._
        0 * remoteAccess2._
        0 * result._
    }

    def "does not attempt to resolve remote dependency when local dependency is missing"() {
        given:
        def repo1 = addRepo1()
        def repo2 = addRepo2()

        when:
        resolver.resolve(moduleComponentId, componentRequestMetaData, result)

        then:
        1 * localAccess.resolveComponentMetaData(moduleComponentId, componentRequestMetaData, _) >> { id, meta, result ->
            result.missing()
        }
        1 * localAccess2.resolveComponentMetaData(moduleComponentId, componentRequestMetaData, _) >> { id, meta, result ->
            result.missing()
            result.authoritative = false
        }
        1 * remoteAccess2.resolveComponentMetaData(moduleComponentId, componentRequestMetaData, _) >> { id, meta, result ->
            result.resolved(componentState)
        }
        1 * result.resolved(_, _) >> { ComponentGraphResolveState state, ComponentGraphSpecificResolveState graphState ->
            assert state == componentState
            assert graphState.repositoryName == repo2.name
        }

        and:
        0 * localAccess._
        0 * remoteAccess._
        0 * localAccess2._
        0 * remoteAccess2._
        0 * result._
    }

    def "attempts to find remote dependency when local dependency is missing or unknown in all repositories"() {
        given:
        def repo1 = addRepo1()
        def repo2 = addRepo2()

        when:
        resolver.resolve(moduleComponentId, componentRequestMetaData, result)

        then:
        1 * localAccess.resolveComponentMetaData(moduleComponentId, componentRequestMetaData, _) >> { id, meta, result ->
            result.missing()
            result.authoritative = false
        }
        1 * localAccess2.resolveComponentMetaData(moduleComponentId, componentRequestMetaData, _)
        1 * remoteAccess2.resolveComponentMetaData(moduleComponentId, componentRequestMetaData, _) >> { id, meta, result ->
            result.missing()
        }
        1 * remoteAccess.resolveComponentMetaData(moduleComponentId, componentRequestMetaData, _) >> { id, meta, result ->
            result.resolved(componentState)
        }
        1 * result.resolved(_, _) >> { ComponentGraphResolveState state, ComponentGraphSpecificResolveState graphState ->
            assert state == componentState
            assert graphState.repositoryName == repo1.name
        }

        and:
        0 * localAccess._
        0 * remoteAccess._
        0 * localAccess2._
        0 * remoteAccess2._
        0 * result._
    }

    def "ignores failure to resolve local dependency when available in another repository"() {
        given:
        def repo1 = addRepo1()
        def repo2 = addRepo2()

        when:
        resolver.resolve(moduleComponentId, componentRequestMetaData, result)

        then:
        1 * localAccess.resolveComponentMetaData(moduleComponentId, componentRequestMetaData, _) >> { id, meta, result ->
            result.failed(new ModuleVersionResolveException(id, broken))
        }
        1 * localAccess2.resolveComponentMetaData(moduleComponentId, componentRequestMetaData, _) >> { id, meta, result ->
            result.resolved(componentState)
        }
        1 * result.resolved(_, _) >> { ComponentGraphResolveState state, ComponentGraphSpecificResolveState graphState ->
            assert state == componentState
            assert graphState.repositoryName == repo2.name
        }

        and:
        0 * localAccess._
        0 * remoteAccess._
        0 * localAccess2._
        0 * remoteAccess2._
        0 * result._
    }

    def "ignores failure to resolve remote dependency when available in another repository"() {
        given:
        def repo1 = addRepo1()
        def repo2 = addRepo2()

        when:
        resolver.resolve(moduleComponentId, componentRequestMetaData, result)

        then:
        1 * localAccess.resolveComponentMetaData(moduleComponentId, componentRequestMetaData, _)
        1 * remoteAccess.resolveComponentMetaData(moduleComponentId, componentRequestMetaData, _) >> { id, meta, result ->
            result.failed(new ModuleVersionResolveException(id, broken))
        }
        1 * localAccess2.resolveComponentMetaData(moduleComponentId, componentRequestMetaData, _)
        1 * remoteAccess2.resolveComponentMetaData(moduleComponentId, componentRequestMetaData, _) >> { id, meta, result ->
            result.resolved(componentState)
        }
        1 * result.resolved(_, _) >> { ComponentGraphResolveState state, ComponentGraphSpecificResolveState graphState ->
            assert state == componentState
            assert graphState.repositoryName == repo2.name
        }

        and:
        0 * localAccess._
        0 * remoteAccess._
        0 * localAccess2._
        0 * remoteAccess2._
        0 * result._
    }

    def "rethrows failure to resolve local dependency when not available in any repository"() {
        given:
        def failure = new ModuleVersionResolveException(Stub(ModuleVersionSelector), broken)
        def repo1 = addRepo1()
        def repo2 = addRepo2()

        when:
        resolver.resolve(moduleComponentId, componentRequestMetaData, result)

        then:
        1 * localAccess.resolveComponentMetaData(moduleComponentId, componentRequestMetaData, _) >> { id, meta, result ->
            result.failed(failure)
        }
        1 * localAccess2.resolveComponentMetaData(moduleComponentId, componentRequestMetaData, _)
        1 * remoteAccess2.resolveComponentMetaData(moduleComponentId, componentRequestMetaData, _) >> { id, meta, result ->
            result.missing()
        }
        1 * result.failed({ it.cause == failure })

        and:
        0 * localAccess._
        0 * remoteAccess._
        0 * localAccess2._
        0 * remoteAccess2._
        0 * result._
    }

    def "rethrows failure to resolve remote dependency when not available in any repository"() {
        given:
        def failure = new ModuleVersionResolveException(Stub(ModuleVersionSelector), broken)
        def repo1 = addRepo1()
        def repo2 = addRepo2()

        when:
        resolver.resolve(moduleComponentId, componentRequestMetaData, result)

        then:
        1 * localAccess.resolveComponentMetaData(moduleComponentId, componentRequestMetaData, _)
        1 * remoteAccess.resolveComponentMetaData(moduleComponentId, componentRequestMetaData, _) >> { id, meta, result ->
            result.failed(failure)
        }
        1 * localAccess2.resolveComponentMetaData(moduleComponentId, componentRequestMetaData, _)
        1 * remoteAccess2.resolveComponentMetaData(moduleComponentId, componentRequestMetaData, _) >> { id, meta, result ->
            result.missing()
        }
        1 * result.failed({ it.cause == failure })

        and:
        0 * localAccess._
        0 * remoteAccess._
        0 * localAccess2._
        0 * remoteAccess2._
        0 * result._
    }

    def metaData(String version) {
        return Stub(ModuleComponentResolveMetadata) {
            toString() >> version
            getId() >> DefaultModuleVersionIdentifier.newId("org", "module", version)
        }
    }
}
