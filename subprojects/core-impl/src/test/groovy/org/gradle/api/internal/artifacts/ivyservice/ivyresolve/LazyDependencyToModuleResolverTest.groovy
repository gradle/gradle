package org.gradle.api.internal.artifacts.ivyservice.ivyresolve

import spock.lang.Specification
import org.gradle.api.internal.artifacts.ivyservice.DependencyToModuleResolver
import org.apache.ivy.plugins.version.VersionMatcher
import org.gradle.api.internal.artifacts.ivyservice.ModuleVersionResolveException
import org.gradle.api.internal.artifacts.ivyservice.ModuleVersionNotFoundException
import org.apache.ivy.core.module.descriptor.DependencyDescriptor
import org.apache.ivy.core.module.descriptor.Artifact
import org.gradle.api.internal.artifacts.ivyservice.ModuleVersionResolver
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.apache.ivy.core.module.descriptor.ModuleDescriptor
import org.apache.ivy.core.module.descriptor.Configuration

class LazyDependencyToModuleResolverTest extends Specification {
    final DependencyToModuleResolver target = Mock()
    final VersionMatcher matcher = Mock()
    final ModuleVersionResolver resolvedModule = Mock()
    final LazyDependencyToModuleResolver resolver = new LazyDependencyToModuleResolver(target, matcher)

    def "wraps failure to resolve module"() {
        def dependency = dependency()
        def failure = new RuntimeException("broken")

        when:
        resolver.create(dependency).descriptor

        then:
        ModuleVersionResolveException e = thrown()
        e.message == "Could not resolve group:group, module:module, version:1.0."
        e.cause == failure

        and:
        1 * target.create(dependency) >> { throw failure }
    }

    def "wraps module not found"() {
        def dependency = dependency()

        when:
        resolver.create(dependency).descriptor

        then:
        ModuleVersionNotFoundException e = thrown()
        e.message == "Could not find group:group, module:module, version:1.0."

        and:
        1 * target.create(dependency) >> null
    }

    def "wraps module not found for missing dynamic version"() {
        def dependency = dependency()

        given:
        matcher.isDynamic(_) >> true

        when:
        resolver.create(dependency).id

        then:
        ModuleVersionResolveException e = thrown()
        e.message == "Could not find any version that matches group:group, module:module, version:1.0."

        and:
        1 * target.create(dependency) >> null
    }

    def "wraps failure to resolve artifact"() {
        def dependency = dependency()
        def artifact = artifact()
        def failure = new RuntimeException("broken")

        when:
        resolver.create(dependency).getArtifact(artifact)

        then:
        ArtifactResolveException e = thrown()
        e.message == "Could not resolve artifact group:group, module:module, version:1.0, name:artifact."
        e.cause == failure

        and:
        1 * target.create(dependency) >> resolvedModule
        _ * resolvedModule.descriptor >> module()
        _ * resolvedModule.getArtifact(artifact) >> { throw failure }
    }

    def "wraps artifact not found failure"() {
        def dependency = dependency()
        def artifact = artifact()

        when:
        resolver.create(dependency).getArtifact(artifact)

        then:
        ArtifactNotFoundException e = thrown()
        e.message == "Artifact group:group, module:module, version:1.0, name:artifact not found."

        and:
        1 * target.create(dependency) >> resolvedModule
        _ * resolvedModule.descriptor >> module()
        _ * resolvedModule.getArtifact(artifact) >> null
    }

    def module() {
        ModuleDescriptor descriptor = Mock()
        ModuleRevisionId id = ModuleRevisionId.newInstance("group", "module", "1.0")
        _ * descriptor.moduleRevisionId >> id
        _ * descriptor.configurations >> ([] as Configuration[])
        return descriptor
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
        return artifact
    }
}
