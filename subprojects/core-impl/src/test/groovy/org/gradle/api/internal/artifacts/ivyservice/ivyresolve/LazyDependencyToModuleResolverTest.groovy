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

import org.apache.ivy.core.module.descriptor.Artifact
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor
import org.apache.ivy.core.module.descriptor.DependencyDescriptor
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.apache.ivy.plugins.version.VersionMatcher
import spock.lang.Specification
import org.gradle.api.internal.artifacts.ivyservice.*

class LazyDependencyToModuleResolverTest extends Specification {
    final DependencyToModuleResolver target = Mock()
    final VersionMatcher matcher = Mock()
    final ModuleVersionResolveResult resolvedModule = Mock()
    final LazyDependencyToModuleResolver resolver = new LazyDependencyToModuleResolver(target, matcher)

    def "does not resolve module for static version dependency until requested"() {
        def dependency = dependency()

        when:
        def idResolveResult = resolver.resolve(dependency)

        then:
        idResolveResult.id == dependency.dependencyRevisionId

        and:
        0 * target._

        when:
        def moduleResolveResult = idResolveResult.resolve()

        then:
        1 * target.resolve(dependency) >> resolvedModule
        1 * resolvedModule.descriptor >> module()
        0 * target._
    }

    def "resolves module for dynamic version dependency immediately"() {
        def dependency = dependency()
        def module = module()

        given:
        matcher.isDynamic(_) >> true

        when:
        def idResolveResult = resolver.resolve(dependency)
        def id = idResolveResult.id

        then:
        id == module.moduleRevisionId

        and:
        1 * target.resolve(dependency) >> resolvedModule
        _ * resolvedModule.id >> module.moduleRevisionId
        _ * resolvedModule.descriptor >> module
        0 * target._

        when:
        def moduleResolveResult = idResolveResult.resolve()

        then:
        0 * target._
    }

    def "does not resolve module more than once"() {
        def dependency = dependency()

        when:
        def idResolveResult = resolver.resolve(dependency)
        idResolveResult.resolve()

        then:
        1 * target.resolve(dependency) >> resolvedModule
        1 * resolvedModule.descriptor >> module()
        0 * target._

        when:
        def moduleResolveResult = idResolveResult.resolve()

        then:
        0 * target._
    }

    def "collects failure to resolve module"() {
        def dependency = dependency()
        def failure = new ModuleVersionResolveException("broken")

        when:
        def idFailureResult = resolver.resolve(dependency)

        then:
        idFailureResult.failure == null;

        and:
        0 * target._

        when:
        def resolveResult = idFailureResult.resolve()

        then:
        resolveResult.failure.is(failure)

        and:
        1 * target.resolve(dependency) >> resolvedModule
        _ * resolvedModule.failure >> failure
        0 * target._

        when:
        resolveResult.descriptor

        then:
        ModuleVersionResolveException e = thrown()
        e.is(resolveResult.failure)

        and:
        0 * target._
    }

    def "collects and wraps module not found"() {
        def dependency = dependency()

        when:
        def resolveResult = resolver.resolve(dependency).resolve()

        then:
        resolveResult.failure instanceof ModuleVersionNotFoundException
        resolveResult.failure.message == "Could not find group:group, module:module, version:1.0."

        and:
        1 * target.resolve(dependency) >> resolvedModule
        _ * resolvedModule.failure >> new ModuleVersionNotFoundException("broken")
    }

    def "collects and wraps unexpected module resolve failure"() {
        def dependency = dependency()
        def failure = new RuntimeException("broken")

        when:
        def resolveResult = resolver.resolve(dependency).resolve()

        then:
        resolveResult.failure instanceof ModuleVersionResolveException
        resolveResult.failure.message == "Could not resolve group:group, module:module, version:1.0."

        and:
        1 * target.resolve(dependency) >> { throw failure }
    }

    def "collects and wraps module not found for missing dynamic version"() {
        def dependency = dependency()

        given:
        matcher.isDynamic(_) >> true

        when:
        def idResolveResult = resolver.resolve(dependency)

        then:
        idResolveResult.failure instanceof ModuleVersionNotFoundException
        idResolveResult.failure.message == "Could not find any version that matches group:group, module:module, version:1.0."

        and:
        1 * target.resolve(dependency) >> resolvedModule
        _ * resolvedModule.failure >> new ModuleVersionNotFoundException("missing")

        when:
        idResolveResult.id

        then:
        ModuleVersionNotFoundException e = thrown()
        e.is(idResolveResult.failure)

        and:
        0 * target._

        when:
        def resolveResult = idResolveResult.resolve()
        resolveResult.descriptor

        then:
        e = thrown()
        e.is(idResolveResult.failure)
    }

    def "can resolve artifact for a module version"() {
        def dependency = dependency()
        def artifact = artifact()
        ArtifactResolver targetResolver = Mock()
        ArtifactResolveResult resolvedArtifact = Mock()

        when:
        def resolveResult = resolver.resolve(dependency).resolve()

        then:
        1 * target.resolve(dependency) >> resolvedModule
        _ * resolvedModule.descriptor >> module()

        when:
        def artifactResult = resolveResult.artifactResolver.resolve(artifact)

        then:
        artifactResult == resolvedArtifact

        and:
        _ * resolvedModule.artifactResolver >> targetResolver
        1 * targetResolver.resolve(artifact) >> resolvedArtifact
    }
    
    def "wraps unexpected failure to resolve artifact"() {
        def dependency = dependency()
        def artifact = artifact()
        ArtifactResolver targetResolver = Mock()
        def failure = new RuntimeException("broken")

        when:
        def resolveResult = resolver.resolve(dependency).resolve()

        then:
        1 * target.resolve(dependency) >> resolvedModule
        _ * resolvedModule.descriptor >> module()

        when:
        def artifactResult = resolveResult.artifactResolver.resolve(artifact)

        then:
        artifactResult.failure instanceof ArtifactResolveException
        artifactResult.failure.message == "Could not download artifact 'group:module:1.0@zip'"
        artifactResult.failure.cause == failure

        and:
        _ * resolvedModule.artifactResolver >> targetResolver
        _ * targetResolver.resolve(artifact) >> { throw failure }

        when:
        artifactResult.file

        then:
        ArtifactResolveException e = thrown()
        e.is(artifactResult.failure)
    }

    def module() {
        ModuleRevisionId id = ModuleRevisionId.newInstance("group", "module", "1.0")
        return new DefaultModuleDescriptor(id, "release", new Date())
    }

    def dependency() {
        DependencyDescriptor descriptor = Mock()
        ModuleRevisionId id = ModuleRevisionId.newInstance("group", "module", "1.0")
        _ * descriptor.dependencyRevisionId >> id
        return descriptor
    }

    def artifact() {
        Artifact artifact = Mock()
        ModuleRevisionId id = ModuleRevisionId.newInstance("group", "module", "1.0")
        _ * artifact.moduleRevisionId >> id
        _ * artifact.name >> 'artifact'
        _ * artifact.ext >> 'zip'
        return artifact
    }
}
