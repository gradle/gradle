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
import org.apache.ivy.plugins.latest.LatestRevisionStrategy
import org.apache.ivy.plugins.resolver.ResolverSettings
import org.apache.ivy.plugins.version.VersionMatcher
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.ivyservice.BuildableModuleVersionResolveResult
import spock.lang.Specification

class UserResolverChainTest extends Specification {
    final UserResolverChain resolver = new UserResolverChain()
    final ModuleRevisionId dependencyId = Stub()
    final DependencyDescriptor dependency = Stub()
    final ModuleDescriptor descriptor = descriptor("1.2")
    final ModuleVersionIdentifier resolvedId = moduleVersionIdentifier(descriptor)

    ModuleVersionIdentifier moduleVersionIdentifier(ModuleDescriptor moduleDescriptor) {
        def moduleRevId = moduleDescriptor.moduleRevisionId
        new DefaultModuleVersionIdentifier(moduleRevId.organisation, moduleRevId.name, moduleRevId.revision)
    }

    final BuildableModuleVersionResolveResult result = Mock()
    final VersionMatcher matcher = Stub()
    final ModuleSource moduleSource = Mock()

    def setup() {
        _ * dependencyId.organisation >> "group"
        _ * dependencyId.name >> "project"
        _ * dependencyId.revision >> "1.0"
        dependency.dependencyRevisionId >> dependencyId
        def settings = Stub(ResolverSettings)
        _ * settings.versionMatcher >> matcher
        _ * settings.defaultLatestStrategy >> new LatestRevisionStrategy();
        resolver.settings = settings
    }

    def "uses local dependency when available"() {
        given:
        def repo = Mock(LocalAwareModuleVersionRepository)
        resolver.add(repo)

        when:
        resolver.resolve(dependency, result)

        then:
        1 * repo.getLocalDependency(dependency, _) >> { dep, result ->
            result.resolved(descriptor, true, moduleSource)
        }
        1 * result.resolved(resolvedId, descriptor, _) >> { resolvedId, descr, rep ->
            assert rep.delegate == repo
            assert rep.moduleSource == moduleSource
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
            result.resolved(descriptor, true, moduleSource)
        }
        1 * result.resolved(resolvedId, descriptor, _) >> { resolvedId, descr, rep ->
            assert rep.delegate == repo
            assert rep.moduleSource == moduleSource
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
            result.resolved(descriptor, true, moduleSource)
        }
        1 * result.resolved(resolvedId, descriptor, _) >> { resolvedId, descr, rep ->
            assert rep.delegate == repo
            assert rep.moduleSource == moduleSource
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
        1 * result.notFound(_ as ModuleVersionIdentifier) >> { ModuleVersionIdentifier mdV ->
            assert mdV.group == "group"
            assert mdV.name == "project"
            assert mdV.version == "1.0"

        }

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
        1 * result.notFound(_ as ModuleVersionIdentifier) >> { ModuleVersionIdentifier moduleVersionIdentifier ->
            assert moduleVersionIdentifier.group == "group"
            assert moduleVersionIdentifier.name == "project"
            assert moduleVersionIdentifier.version == "1.0"
        }

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
        def version2 = descriptor("1.2")
        resolver.add(repo1)
        resolver.add(repo2)
        resolver.add(repo3)

        when:
        resolver.resolve(dependency, result)

        then:
        1 * repo1.getLocalDependency(dependency, _) >> { dep, result ->
            result.resolve(descriptor("1.1"), null)
        }
        1 * repo2.getLocalDependency(dependency, _) >> { dep, result ->
            result.resolved(version2, true, moduleSource)
        }
        1 * repo3.getLocalDependency(dependency, _) >> { dep, result ->
            result.resolved(descriptor("1.0"), true, null)
        }
        1 * result.resolved(moduleVersionIdentifier(version2), version2, _) >> { revision, descriptor, repo ->
            assert repo.delegate == repo2
            assert repo.moduleSource == moduleSource
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
            result.resolved(descriptor, true, moduleSource)
        }
        1 * result.resolved(resolvedId, descriptor, _) >> { revisionId, descriptor, repo ->
            assert repo.delegate == repo1
            assert repo.moduleSource == moduleSource
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
            result.resolved(descriptor, true, moduleSource)
        }
        1 * result.resolved(resolvedId, descriptor, _) >> { resolvedId, descr, repo ->
            assert repo.delegate == repo2
            assert repo.moduleSource == moduleSource
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
            result.resolved(descriptor, true, moduleSource)
        }
        1 * result.resolved(resolvedId, descriptor, _) >> { resolvedId, descr, repo ->
            assert repo.delegate == repo2
            assert repo.moduleSource == moduleSource
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
            result.resolved(descriptor, true, moduleSource)
        }
        1 * result.resolved(resolvedId, descriptor, _) >> { resolvedId, descr, repo ->
            assert repo.delegate == repo2
            assert repo.moduleSource == moduleSource
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
            result.resolved(descriptor, true, moduleSource)
        }
        1 * result.resolved(resolvedId, descriptor, _) >> { resolvedId, descr, repo ->
            assert repo.delegate == repo2
            assert repo.moduleSource == moduleSource
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
            result.resolved(descriptor, true, moduleSource)
        }
        1 * result.resolved(resolvedId, descriptor, _) >> { resolvedId, descr, repo ->
            assert repo.delegate == repo2
            assert repo.moduleSource == moduleSource
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
            result.resolved(descriptor, true, moduleSource)
        }
        1 * result.resolved(resolvedId, descriptor, _) >> { resolvedId, descr, repo ->
            assert repo.delegate == repo1
            assert repo.moduleSource == moduleSource
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
            result.resolved(descriptor, true, moduleSource)
        }
        1 * result.resolved(resolvedId, descriptor, _) >> { resolvedId, descr, repo ->
            assert repo.delegate == repo2
            assert repo.moduleSource == moduleSource
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
            result.resolved(descriptor, true, moduleSource)
        }
        1 * result.resolved(resolvedId, descriptor, _) >> { resolvedId, descr, repo ->
            assert repo.delegate == repo2
            assert repo.moduleSource == moduleSource
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

    def descriptor(def version) {
        def descriptor = Stub(ModuleDescriptor)
        descriptor.resolvedModuleRevisionId >> ModuleRevisionId.newInstance("org", "module", version)
        return descriptor
    }
}
