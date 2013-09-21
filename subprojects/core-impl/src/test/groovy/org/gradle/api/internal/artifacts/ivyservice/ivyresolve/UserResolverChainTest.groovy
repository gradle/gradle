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

import org.apache.ivy.core.module.descriptor.DependencyDescriptor
import org.apache.ivy.core.module.descriptor.ModuleDescriptor
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ModuleVersionSelector
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.ivyservice.BuildableModuleVersionResolveResult
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.LatestStrategy
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionMatcher
import org.gradle.api.internal.artifacts.metadata.DependencyMetaData
import org.gradle.api.internal.artifacts.metadata.MutableModuleVersionMetaData
import spock.lang.Specification

class UserResolverChainTest extends Specification {
    final metaData = metaData("1.2")
    final dependencyId = Stub(ModuleVersionSelector)
    final dependency = Stub(DependencyMetaData)
    final dependencyDescriptor = Stub(DependencyDescriptor)
    final matcher = Stub(VersionMatcher)
    final latestStrategy = Stub(LatestStrategy) {
        compare(_, _) >> { a, b ->
            a.version.compareTo(b.version)
        }
    }
    final result = Mock(BuildableModuleVersionResolveResult)
    final moduleSource = Mock(ModuleSource)

    final UserResolverChain resolver = new UserResolverChain(matcher, latestStrategy)

    ModuleVersionIdentifier moduleVersionIdentifier(ModuleDescriptor moduleDescriptor) {
        def moduleRevId = moduleDescriptor.moduleRevisionId
        new DefaultModuleVersionIdentifier(moduleRevId.organisation, moduleRevId.name, moduleRevId.revision)
    }

    def setup() {
        _ * dependencyId.group >> "group"
        _ * dependencyId.name >> "project"
        _ * dependencyId.version >> "1.0"
        _ * dependency.requested >> dependencyId
        _ * dependency.descriptor >> dependencyDescriptor
    }

    def "uses local dependency when available"() {
        given:
        def repo = Mock(LocalAwareModuleVersionRepository)
        resolver.add(repo)

        when:
        resolver.resolve(dependency, result)

        then:
        1 * repo.getLocalDependency(dependency, _) >> { dep, result ->
            result.resolved(metaData, moduleSource)
        }
        1 * result.resolved(_, _) >> { metaData, source ->
            assert metaData == this.metaData
            assert source.delegate == repo
            assert source.moduleSource == moduleSource
        }

        and:
        _ * repo.name >> "repo"
        0 * repo._
        0 * result._
    }

    def "attempts to find remote dependency when local dependency is unknown"() {
        given:
        def repo = Mock(LocalAwareModuleVersionRepository)
        resolver.add(repo)

        when:
        resolver.resolve(dependency, result)

        then:
        1 * repo.getLocalDependency(dependency, _)
        1 * repo.getDependency(dependency, _) >> { dep, result ->
            result.resolved(metaData, moduleSource)
        }
        1 * result.resolved(_, _) >> { metaData, source ->
            assert metaData == this.metaData
            assert source.delegate == repo
            assert source.moduleSource == moduleSource
        }
        and:
        _ * repo.name >> "repo"
        0 * repo._
        0 * result._
    }

    def "attempts to find remote dependency when local dependency is probably missing"() {
        given:
        def repo = Mock(LocalAwareModuleVersionRepository)
        resolver.add(repo)

        when:
        resolver.resolve(dependency, result)

        then:
        1 * repo.getLocalDependency(dependency, _) >> { dep, result ->
            result.probablyMissing()
        }
        1 * repo.getDependency(dependency, _) >> { dep, result ->
            result.resolved(metaData, moduleSource)
        }
        1 * result.resolved(_, _) >> { metaData, source ->
            assert metaData == this.metaData
            assert source.delegate == repo
            assert source.moduleSource == moduleSource
        }

        and:
        _ * repo.name >> "repo"
        0 * repo._
        0 * result._
    }

    def "fails with not found when local dependency is marked as missing"() {
        given:
        def repo = Mock(LocalAwareModuleVersionRepository)
        resolver.add(repo)

        when:
        resolver.resolve(dependency, result)

        then:
        1 * repo.getLocalDependency(dependency, _) >> { dep, result ->
            result.missing()
        }
        1 * result.notFound(dependencyId)

        and:
        _ * repo.name >> "repo"
        0 * repo._
        0 * result._
    }

    def "fails with not found when local and remote dependency marked as missing"() {
        given:
        def repo = Mock(LocalAwareModuleVersionRepository)
        resolver.add(repo)

        when:
        resolver.resolve(dependency, result)

        then:
        1 * repo.getLocalDependency(dependency, _) >> { dep, result ->
            result.probablyMissing()
        }
        1 * repo.getDependency(dependency, _) >> { dep, result ->
            result.missing()
        }
        1 * result.notFound(dependencyId)

        and:
        _ * repo.name >> "repo"
        0 * repo._
        0 * result._
    }

    def "searches all repositories for a dynamic version"() {
        given:
        _ * matcher.isDynamic(_) >> true
        def repo1 = Mock(LocalAwareModuleVersionRepository)
        def repo2 = Mock(LocalAwareModuleVersionRepository)
        def repo3 = Mock(LocalAwareModuleVersionRepository)
        def version2 = metaData("1.2")
        resolver.add(repo1)
        resolver.add(repo2)
        resolver.add(repo3)

        when:
        resolver.resolve(dependency, result)

        then:
        1 * repo1.getLocalDependency(dependency, _) >> { dep, result ->
            result.resolved(metaData("1.1"), null)
        }
        1 * repo2.getLocalDependency(dependency, _) >> { dep, result ->
            result.resolved(version2, moduleSource)
        }
        1 * repo3.getLocalDependency(dependency, _) >> { dep, result ->
            result.resolved(metaData("1.0"), null)
        }
        1 * result.resolved(_, _) >> { metaData, source ->
            assert metaData == version2
            assert source.delegate == repo2
            assert source.moduleSource == moduleSource
        }

        and:
        _ * repo1.name >> "repo1"
        _ * repo2.name >> "repo2"
        _ * repo3.name >> "repo3"
        0 * repo1._
        0 * repo2._
        0 * repo3._
        0 * result._
    }

    def "stops on first available local dependency for static version"() {
        given:
        _ * matcher.isDynamic(_) >> false
        def repo1 = Mock(LocalAwareModuleVersionRepository)
        def repo2 = Mock(LocalAwareModuleVersionRepository)
        def repo3 = Mock(LocalAwareModuleVersionRepository)
        resolver.add(repo1)
        resolver.add(repo2)
        resolver.add(repo3)

        when:
        resolver.resolve(dependency, result)

        then:
        1 * repo1.getLocalDependency(dependency, _) >> { dep, result ->
            result.resolved(metaData, moduleSource)
        }
        1 * result.resolved(_, _) >> { metaData, source ->
            assert metaData == this.metaData
            assert source.delegate == repo1
            assert source.moduleSource == moduleSource
        }

        and:
        _ * repo1.name >> "repo1"
        _ * repo2.name >> "repo2"
        _ * repo3.name >> "repo3"
        0 * repo1._
        0 * repo2._
        0 * repo3._
        0 * result._
    }

    def "uses local dependency when available in one repository and missing from all other repositories"() {
        given:
        def repo1 = Mock(LocalAwareModuleVersionRepository)
        def repo2 = Mock(LocalAwareModuleVersionRepository)
        resolver.add(repo1)
        resolver.add(repo2)

        when:
        resolver.resolve(dependency, result)

        then:
        1 * repo1.getLocalDependency(dependency, _) >> { dep, result ->
            result.missing()
        }
        1 * repo2.getLocalDependency(dependency, _) >> { dep, result ->
            result.resolved(metaData, moduleSource)
        }
        1 * result.resolved(_, _) >> { metaData, source ->
            assert metaData == this.metaData
            assert source.delegate == repo2
            assert source.moduleSource == moduleSource
        }

        and:
        _ * repo1.name >> "repo"
        _ * repo2.name >> "repo"
        0 * repo1._
        0 * repo2._
        0 * result._
    }

    def "uses local dependency when available in one repository and probably missing in all other repositories"() {
        given:
        def repo1 = Mock(LocalAwareModuleVersionRepository)
        def repo2 = Mock(LocalAwareModuleVersionRepository)
        resolver.add(repo1)
        resolver.add(repo2)

        when:
        resolver.resolve(dependency, result)

        then:
        1 * repo1.getLocalDependency(dependency, _) >> { dep, result ->
            result.probablyMissing()
        }
        1 * repo2.getLocalDependency(dependency, _) >> { dep, result ->
            result.resolved(metaData, moduleSource)
        }
        1 * result.resolved(_, _) >> { metaData, source ->
            assert metaData == this.metaData
            assert source.delegate == repo2
            assert source.moduleSource == moduleSource
        }
        and:
        _ * repo1.name >> "repo"
        _ * repo2.name >> "repo"
        0 * repo1._
        0 * repo2._
        0 * result._
    }

    def "uses remote dependency when local dependency is unknown for a given repository and probably missing in other repositories"() {
        given:
        def repo1 = Mock(LocalAwareModuleVersionRepository)
        def repo2 = Mock(LocalAwareModuleVersionRepository)
        resolver.add(repo1)
        resolver.add(repo2)

        when:
        resolver.resolve(dependency, result)

        then:
        1 * repo1.getLocalDependency(dependency, _) >> { dep, result ->
            result.probablyMissing()
        }
        1 * repo2.getLocalDependency(dependency, _)
        1 * repo2.getDependency(dependency, _) >> { dep, result ->
            result.resolved(metaData, moduleSource)
        }
        1 * result.resolved(_, _) >> { metaData, source ->
            assert metaData == this.metaData
            assert source.delegate == repo2
            assert source.moduleSource == moduleSource
        }
        and:
        _ * repo1.name >> "repo"
        _ * repo2.name >> "repo"
        0 * repo1._
        0 * repo2._
        0 * result._
    }

    def "attempts to find remote dependency when local dependency is probably missing in all repositories"() {
        given:
        def repo1 = Mock(LocalAwareModuleVersionRepository)
        def repo2 = Mock(LocalAwareModuleVersionRepository)
        resolver.add(repo1)
        resolver.add(repo2)

        when:
        resolver.resolve(dependency, result)

        then:
        1 * repo1.getLocalDependency(dependency, _) >> { dep, result ->
            result.probablyMissing()
        }
        1 * repo2.getLocalDependency(dependency, _) >> { dep, result ->
            result.probablyMissing()
        }
        1 * repo1.getDependency(dependency, _) >> { dep, result ->
            result.missing()
        }
        1 * repo2.getDependency(dependency, _) >> { dep, result ->
            result.resolved(metaData, moduleSource)
        }
        1 * result.resolved(_, _) >> { metaData, source ->
            assert metaData == this.metaData
            assert source.delegate == repo2
            assert source.moduleSource == moduleSource
        }
        and:
        _ * repo1.name >> "repo"
        _ * repo2.name >> "repo"
        0 * repo1._
        0 * repo2._
        0 * result._
    }

    def "does not attempt to resolve remote dependency when local dependency is missing"() {
        given:
        def repo1 = Mock(LocalAwareModuleVersionRepository)
        def repo2 = Mock(LocalAwareModuleVersionRepository)
        resolver.add(repo1)
        resolver.add(repo2)

        when:
        resolver.resolve(dependency, result)

        then:
        1 * repo1.getLocalDependency(dependency, _) >> { dep, result ->
            result.missing()
        }
        1 * repo2.getLocalDependency(dependency, _) >> { dep, result ->
            result.probablyMissing()
        }
        1 * repo2.getDependency(dependency, _) >> { dep, result ->
            result.resolved(metaData, moduleSource)
        }
        1 * result.resolved(_, _) >> { metaData, source ->
            assert metaData == this.metaData
            assert source.delegate == repo2
            assert source.moduleSource == moduleSource
        }

        and:
        _ * repo1.name >> "repo"
        _ * repo2.name >> "repo"
        0 * repo1._
        0 * repo2._
        0 * result._
    }

    def "attempts to find remote dependency when local dependency is missing or unknown in all repositories"() {
        given:
        def repo1 = Mock(LocalAwareModuleVersionRepository)
        def repo2 = Mock(LocalAwareModuleVersionRepository)
        resolver.add(repo1)
        resolver.add(repo2)

        when:
        resolver.resolve(dependency, result)

        then:
        1 * repo1.getLocalDependency(dependency, _) >> { dep, result ->
            result.probablyMissing()
        }
        1 * repo2.getLocalDependency(dependency, _)
        1 * repo2.getDependency(dependency, _) >> { dep, result ->
            result.missing()
        }
        1 * repo1.getDependency(dependency, _) >> { dep, result ->
            result.resolved(metaData, moduleSource)
        }
        1 * result.resolved(_, _) >> { metaData, source ->
            assert metaData == this.metaData
            assert source.delegate == repo1
            assert source.moduleSource == moduleSource
        }
        and:
        _ * repo1.name >> "repo"
        _ * repo2.name >> "repo"
        0 * repo1._
        0 * repo2._
        0 * result._
    }

    def "ignores failure to resolve local dependency when available in another repository"() {
        given:
        def repo1 = Mock(LocalAwareModuleVersionRepository)
        def repo2 = Mock(LocalAwareModuleVersionRepository)
        resolver.add(repo1)
        resolver.add(repo2)

        when:
        resolver.resolve(dependency, result)

        then:
        1 * repo1.getLocalDependency(dependency, _) >> { dep, result ->
            throw new RuntimeException("broken")
        }
        1 * repo2.getLocalDependency(dependency, _) >> { dep, result ->
            result.resolved(metaData, moduleSource)
        }
        1 * result.resolved(_, _) >> { metaData, source ->
            assert metaData == this.metaData
            assert source.delegate == repo2
            assert source.moduleSource == moduleSource
        }

        and:
        _ * repo1.name >> "repo"
        _ * repo2.name >> "repo"
        0 * repo1._
        0 * repo2._
        0 * result._
    }

    def "ignores failure to resolve remote dependency when available in another repository"() {
        given:
        def repo1 = Mock(LocalAwareModuleVersionRepository)
        def repo2 = Mock(LocalAwareModuleVersionRepository)
        resolver.add(repo1)
        resolver.add(repo2)

        when:
        resolver.resolve(dependency, result)

        then:
        1 * repo1.getLocalDependency(dependency, _)
        1 * repo1.getDependency(dependency, _) >> { dep, result ->
            throw new RuntimeException("broken")
        }
        1 * repo2.getLocalDependency(dependency, _)
        1 * repo2.getDependency(dependency, _) >> { dep, result ->
            result.resolved(metaData, moduleSource)
        }
        1 * result.resolved(_, _) >> { metaData, source ->
            assert metaData == this.metaData
            assert source.delegate == repo2
            assert source.moduleSource == moduleSource
        }

        and:
        _ * repo1.name >> "repo"
        _ * repo2.name >> "repo"
        0 * repo1._
        0 * repo2._
        0 * result._
    }

    def "rethrows failure to resolve local dependency when not available in any repository"() {
        given:
        def failure = new RuntimeException("broken")
        def repo1 = Mock(LocalAwareModuleVersionRepository)
        def repo2 = Mock(LocalAwareModuleVersionRepository)
        resolver.add(repo1)
        resolver.add(repo2)

        when:
        resolver.resolve(dependency, result)

        then:
        1 * repo1.getLocalDependency(dependency, _) >> { dep, result ->
            throw failure
        }
        1 * repo2.getLocalDependency(dependency, _)
        1 * repo2.getDependency(dependency, _) >> { dep, result ->
            result.missing()
        }
        1 * result.failed({ it.cause == failure })

        and:
        _ * repo1.name >> "repo"
        _ * repo2.name >> "repo"
        0 * repo1._
        0 * repo2._
        0 * result._
    }

    def "rethrows failure to resolve remote dependency when not available in any repository"() {
        given:
        def failure = new RuntimeException("broken")
        def repo1 = Mock(LocalAwareModuleVersionRepository)
        def repo2 = Mock(LocalAwareModuleVersionRepository)
        resolver.add(repo1)
        resolver.add(repo2)

        when:
        resolver.resolve(dependency, result)

        then:
        1 * repo1.getLocalDependency(dependency, _)
        1 * repo1.getDependency(dependency, _) >> { dep, result ->
            throw failure
        }
        1 * repo2.getLocalDependency(dependency, _)
        1 * repo2.getDependency(dependency, _) >> { dep, result ->
            result.missing()
        }
        1 * result.failed({ it.cause == failure })

        and:
        _ * repo1.name >> "repo"
        _ * repo2.name >> "repo"
        0 * repo1._
        0 * repo2._
        0 * result._
    }

    def descriptor(String version) {
        def descriptor = Stub(ModuleDescriptor)
        descriptor.resolvedModuleRevisionId >> ModuleRevisionId.newInstance("org", "module", version)
        return descriptor
    }

    def metaData(String version) {
        return Stub(MutableModuleVersionMetaData) {
            toString() >> version
            getId() >> DefaultModuleVersionIdentifier.newId("org", "module", version)
            getDescriptor() >> descriptor(version)
        }
    }
}
