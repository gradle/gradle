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

import spock.lang.Specification
import org.apache.ivy.core.module.descriptor.DependencyDescriptor
import org.gradle.api.internal.artifacts.ivyservice.BuildableModuleVersionResolveResult
import org.apache.ivy.plugins.resolver.ResolverSettings
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.apache.ivy.core.module.descriptor.ModuleDescriptor

class UserResolverChainTest extends Specification {
    final UserResolverChain resolver = new UserResolverChain()
    final ModuleRevisionId dependencyId = Stub()
    final DependencyDescriptor dependency = Stub()
    final ModuleRevisionId resolvedId = Stub()
    final ModuleDescriptor descriptor = Stub()
    final BuildableModuleVersionResolveResult result = Mock()

    def setup() {
        dependency.dependencyRevisionId >> dependencyId
        descriptor.resolvedModuleRevisionId >> resolvedId
        resolver.settings = Stub(ResolverSettings)
    }

    def "uses local dependency when available"() {
        given:
        def repo = Mock(LocalAwareModuleVersionRepository)
        resolver.add(repo)

        when:
        resolver.resolve(dependency, result)

        then:
        1 * repo.getLocalDependency(dependency, _) >> { dep, result ->
            result.resolved(descriptor, true)
        }
        1 * result.resolved(resolvedId, descriptor, repo)

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
            result.resolved(descriptor, true)
        }
        1 * result.resolved(resolvedId, descriptor, repo)

        and:
        _ * repo.name >> "repo"
        0 * repo._
        0 * result._
    }

    def "attempts to find remote dependency when local dependency is missing"() {
        given:
        def repo = Mock(LocalAwareModuleVersionRepository)
        resolver.add(repo)

        when:
        resolver.resolve(dependency, result)

        then:
        1 * repo.getLocalDependency(dependency, _) >> { dep, result ->
            result.missing()
        }
        1 * repo.getDependency(dependency, _) >> { dep, result ->
            result.resolved(descriptor, true)
        }
        1 * result.resolved(resolvedId, descriptor, repo)

        and:
        _ * repo.name >> "repo"
        0 * repo._
        0 * result._
    }

    def "fails when not found in any repository"() {
        given:
        def repo = Mock(LocalAwareModuleVersionRepository)
        resolver.add(repo)

        when:
        resolver.resolve(dependency, result)

        then:
        1 * repo.getLocalDependency(dependency, _)
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

    }

    def "stops on first available local dependency for static version"() {

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
            result.resolved(descriptor, true)
        }
        1 * result.resolved(resolvedId, descriptor, repo2)

        and:
        _ * repo1.name >> "repo"
        _ * repo2.name >> "repo"
        0 * repo1._
        0 * repo2._
        0 * result._
    }

    def "uses remote dependency when local dependency is unknown for a given repository and missing in other repositories"() {
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
        1 * repo2.getLocalDependency(dependency, _)
        1 * repo2.getDependency(dependency, _) >> { dep, result ->
            result.resolved(descriptor, true)
        }
        1 * result.resolved(resolvedId, descriptor, repo2)

        and:
        _ * repo1.name >> "repo"
        _ * repo2.name >> "repo"
        0 * repo1._
        0 * repo2._
        0 * result._
    }

    def "attempts to find remote dependency when local dependency is missing in all repositories"() {
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
            result.missing()
        }
        1 * repo1.getDependency(dependency, _) >> { dep, result ->
            result.missing()
        }
        1 * repo2.getDependency(dependency, _) >> { dep, result ->
            result.resolved(descriptor, true)
        }
        1 * result.resolved(resolvedId, descriptor, repo2)

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
            result.missing()
        }
        1 * repo2.getLocalDependency(dependency, _)
        1 * repo2.getDependency(dependency, _) >> { dep, result ->
            result.missing()
        }
        1 * repo1.getDependency(dependency, _) >> { dep, result ->
            result.resolved(descriptor, true)
        }
        1 * result.resolved(resolvedId, descriptor, repo1)

        and:
        _ * repo1.name >> "repo"
        _ * repo2.name >> "repo"
        0 * repo1._
        0 * repo2._
        0 * result._
    }

    def "ignores failure to resolve local dependency when available in another repository"() {

    }

    def "ignores failure to resolve remote dependency when available in another repository"() {

    }

    def "rethrows failure to resolve local dependency when not available in any repository"() {

    }

    def "rethrows failure to resolve remote dependency when not available in any repository"() {

    }
}
