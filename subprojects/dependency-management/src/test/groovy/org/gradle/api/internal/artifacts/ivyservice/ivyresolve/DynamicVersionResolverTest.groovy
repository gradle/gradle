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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve

import org.apache.ivy.core.module.descriptor.DependencyDescriptor
import org.apache.ivy.core.module.descriptor.ModuleDescriptor
import org.gradle.api.Transformer
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ModuleVersionSelector
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector
import org.gradle.api.internal.artifacts.ivyservice.IvyUtil
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.external.model.ModuleComponentResolveMetaData
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetaData
import org.gradle.internal.component.model.DependencyMetaData
import org.gradle.internal.resolve.result.BuildableComponentIdResolveResult
import org.gradle.internal.resolve.result.DefaultModuleVersionListing
import org.gradle.internal.resolve.result.ModuleVersionListing
import spock.lang.Specification

class DynamicVersionResolverTest extends Specification {
    final metaData = metaData("1.2")
    final moduleComponentId = DefaultModuleComponentIdentifier.newId("group", "project", "1.0")
    final dependency = Stub(DependencyMetaData)
    final selector = DefaultModuleVersionSelector.newSelector("group", "project", "1.0")
    final moduleVersionId = DefaultModuleVersionIdentifier.newId("group", "project", "1.0")
    final dependencyDescriptor = Stub(DependencyDescriptor)

    final Transformer<ModuleComponentResolveMetaData, RepositoryChainModuleResolution> transformer = Mock(Transformer)
    final result = Mock(BuildableComponentIdResolveResult)
    def localAccess = Mock(ModuleComponentRepositoryAccess)
    def remoteAccess = Mock(ModuleComponentRepositoryAccess)
    def localAccess2 = Mock(ModuleComponentRepositoryAccess)
    def remoteAccess2 = Mock(ModuleComponentRepositoryAccess)

    final def componentSelectionStrategy = Mock(ComponentChooser)
    final def resolver = new DynamicVersionResolver(componentSelectionStrategy, transformer)

    ModuleVersionIdentifier moduleVersionIdentifier(ModuleDescriptor moduleDescriptor) {
        def moduleRevId = moduleDescriptor.moduleRevisionId
        new DefaultModuleVersionIdentifier(moduleRevId.organisation, moduleRevId.name, moduleRevId.revision)
    }

    def setup() {
        _ * dependency.requested >> selector
        _ * dependency.descriptor >> dependencyDescriptor
    }

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

    def "chooses best component from single repository for dynamic dependency"() {
        given:
        def repo = addRepo1()

        and:
        def dynamicDependency = Mock(DependencyMetaData)
        def dynamicSelector = Mock(ModuleVersionSelector)
        final versionListing = new DefaultModuleVersionListing("1.1")
        final selectedId = DefaultModuleComponentIdentifier.newId("group", "name", "1.1")

        when:
        resolver.resolve(dynamicDependency, result)

        then:
        _ * dynamicDependency.getRequested() >> dynamicSelector
        1 * localAccess.listModuleVersions(dynamicDependency, _) >> { dep, result ->
            result.listed(versionListing)
        }
        _ * componentSelectionStrategy.choose(versionListing, dynamicDependency, localAccess) >> selectedId
        1 * dynamicDependency.withRequestedVersion("1.1") >> dependency
        1 * localAccess.resolveComponentMetaData(dependency, selectedId, _) >> { dep, id, result ->
            result.resolved(metaData)
        }
        1 * transformer.transform(_) >> { RepositoryChainModuleResolution it ->
            assert it.module == metaData
            assert it.repository == repo
            metaData
        }
        1 * result.resolved(_) >> { ModuleComponentResolveMetaData metaData ->
            assert metaData == this.metaData
        }

        and:
        0 * localAccess._
        0 * remoteAccess._
        0 * result._
    }

    def "chooses best component from multiple repositories for dynamic dependency"() {
        given:
        def repo1 = addRepo1()
        def repo2 = addRepo2()

        and:
        def dynamicDependency = Mock(DependencyMetaData)
        def dynamicSelector = Mock(ModuleVersionSelector)
        final versionListing1 = Mock(ModuleVersionListing)
        final versionListing2 = new DefaultModuleVersionListing("1.1")
        final selectedId = DefaultModuleComponentIdentifier.newId("group", "name", "1.1")

        when:
        resolver.resolve(dynamicDependency, result)

        then:
        _ * dynamicDependency.getRequested() >> dynamicSelector
        1 * localAccess.listModuleVersions(dynamicDependency, _) >> { dep, result ->
            result.listed(versionListing1)
        }
        1 * componentSelectionStrategy.choose(versionListing1, dynamicDependency, localAccess) >> null

        1 * localAccess2.listModuleVersions(dynamicDependency, _) >> { dep, result ->
            result.listed(versionListing2)
        }
        1 * componentSelectionStrategy.choose(versionListing2, dynamicDependency, localAccess2) >> selectedId
        1 * dynamicDependency.withRequestedVersion("1.1") >> dependency
        1 * localAccess2.resolveComponentMetaData(dependency, selectedId, _) >> { dep, id, result ->
            result.resolved(metaData)
        }
        1 * transformer.transform(_) >> { RepositoryChainModuleResolution it ->
            assert it.module == metaData
            assert it.repository == repo2
            metaData
        }
        1 * result.resolved(_) >> { ModuleComponentResolveMetaData metaData ->
            assert metaData == this.metaData
        }

        and:
        0 * localAccess._
        0 * remoteAccess._
        0 * result._
    }

    def "fails with not found when local dynamic dependency is marked as missing"() {
        given:
        def repo = addRepo1()

        when:
        resolver.resolve(dependency, result)

        then:
        1 * localAccess.listModuleVersions(dependency, _) >> { dep, result ->
            result.attempted('somewhere')
            result.listed(new DefaultModuleVersionListing())
        }
        1 * result.attempted('somewhere')
        1 * result.notFound(selector)

        and:
        0 * localAccess._
        0 * remoteAccess._
        0 * result._
    }

    def descriptor(String version) {
        def descriptor = Stub(ModuleDescriptor)
        descriptor.resolvedModuleRevisionId >> IvyUtil.createModuleRevisionId("org", "module", version)
        return descriptor
    }

    def metaData(String version) {
        return Stub(MutableModuleComponentResolveMetaData) {
            toString() >> version
            getId() >> DefaultModuleVersionIdentifier.newId("org", "module", version)
            getDescriptor() >> descriptor(version)
        }
    }
}
