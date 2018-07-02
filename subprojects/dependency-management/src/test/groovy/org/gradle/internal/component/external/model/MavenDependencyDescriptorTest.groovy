/*
 * Copyright 2016 the original author or authors.
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

/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.internal.component.external.model

import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.internal.artifacts.DefaultImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusions
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.PatternMatchers
import org.gradle.internal.component.external.descriptor.DefaultExclude
import org.gradle.internal.component.external.descriptor.MavenScope
import org.gradle.internal.component.external.model.maven.MavenDependencyDescriptor
import org.gradle.internal.component.model.ComponentArtifactMetadata
import org.gradle.internal.component.model.ComponentResolveMetadata
import org.gradle.internal.component.model.ConfigurationMetadata
import org.gradle.internal.component.model.ConfigurationNotFoundException
import org.gradle.internal.component.model.Exclude
import org.gradle.internal.component.model.ExcludeMetadata

class MavenDependencyDescriptorTest extends ExternalDependencyDescriptorTest {
    final ModuleExclusions moduleExclusions = new ModuleExclusions(new DefaultImmutableModuleIdentifierFactory())

    @Override
    ExternalDependencyDescriptor create(ModuleComponentSelector selector) {
        return mavenDependencyMetadata(MavenScope.Compile, false, selector, [])
    }

    ExternalDependencyDescriptor createWithExcludes(ModuleComponentSelector selector, List<Exclude> excludes) {
        return mavenDependencyMetadata(MavenScope.Compile, false, selector, excludes)
    }

    def "excludes nothing when no exclude rules provided"() {
        def dep = createWithExcludes(requested, [])

        expect:
        def exclusions = moduleExclusions.excludeAny(dep.allExcludes)
        exclusions == ModuleExclusions.excludeNone()
        exclusions.is(moduleExclusions.excludeAny(dep.allExcludes))
    }

    def "applies exclude rules when traversing a configuration"() {
        def exclude1 = new DefaultExclude(DefaultModuleIdentifier.newId("group1", "*"), ["from"] as String[], PatternMatchers.EXACT)
        def exclude2 = new DefaultExclude(DefaultModuleIdentifier.newId("group2", "*"), ["from"] as String[], PatternMatchers.EXACT)
        def dep = createWithExcludes(requested, [exclude1, exclude2])

        expect:
        def exclusions = moduleExclusions.excludeAny(dep.allExcludes)
        exclusions == moduleExclusions.excludeAny(exclude1, exclude2)
        exclusions.is(moduleExclusions.excludeAny(dep.allExcludes))
    }

    def "selects compile and master configurations from target when traversing from compile configuration"() {
        def fromComponent = Stub(ComponentIdentifier)
        def toComponent = Stub(ComponentResolveMetadata)
        def fromCompile = Stub(ConfigurationMetadata)
        def toCompile = Stub(ConfigurationMetadata)
        def toMaster = Stub(ConfigurationMetadata)
        fromCompile.name >> "compile"
        toComponent.getConfiguration("compile") >> toCompile
        toComponent.getConfiguration("master") >> toMaster
        toMaster.artifacts >> [Stub(ComponentArtifactMetadata)]

        def dep = mavenDependencyMetadata(MavenScope.Compile, false, Stub(ModuleComponentSelector), [])

        expect:
        dep.selectLegacyConfigurations(fromComponent, fromCompile, toComponent) as List == [toCompile, toMaster]
    }

    def "selects compile, runtime and master configurations from target when traversing from other configuration"() {
        def fromComponent = Stub(ComponentIdentifier)
        def toComponent = Stub(ComponentResolveMetadata)
        def fromRuntime = Stub(ConfigurationMetadata)
        def fromRuntime2 = Stub(ConfigurationMetadata)
        def toRuntime = Stub(ConfigurationMetadata)
        def toCompile = Stub(ConfigurationMetadata)
        def toMaster = Stub(ConfigurationMetadata)
        fromRuntime.name >> "runtime"
        fromRuntime2.name >> "provided"
        toComponent.getConfiguration("runtime") >> toRuntime
        toComponent.getConfiguration("compile") >> toCompile
        toComponent.getConfiguration("master") >> toMaster
        toMaster.artifacts >> [Stub(ComponentArtifactMetadata)]

        def dep = mavenDependencyMetadata(MavenScope.Compile, false, Stub(ModuleComponentSelector), [])

        expect:
        dep.selectLegacyConfigurations(fromComponent, fromRuntime, toComponent) as List == [toRuntime, toCompile, toMaster]
        dep.selectLegacyConfigurations(fromComponent, fromRuntime2, toComponent) as List == [toRuntime, toCompile, toMaster]
    }

    def "selects runtime and master configurations from target when traversing from other configuration and target's runtime extends compile"() {
        def fromComponent = Stub(ComponentIdentifier)
        def toComponent = Stub(ComponentResolveMetadata)
        def fromRuntime = Stub(ConfigurationMetadata)
        def fromRuntime2 = Stub(ConfigurationMetadata)
        def toRuntime = Stub(ConfigurationMetadata)
        def toMaster = Stub(ConfigurationMetadata)
        fromRuntime.name >> "runtime"
        fromRuntime2.name >> "provided"
        toComponent.getConfiguration("runtime") >> toRuntime
        toComponent.getConfiguration("master") >> toMaster
        toRuntime.hierarchy >> ["runtime", "compile"]
        toMaster.artifacts >> [Stub(ComponentArtifactMetadata)]

        def dep = mavenDependencyMetadata(MavenScope.Compile, false, Stub(ModuleComponentSelector), [])

        expect:
        dep.selectLegacyConfigurations(fromComponent, fromRuntime, toComponent) as List == [toRuntime, toMaster]
        dep.selectLegacyConfigurations(fromComponent, fromRuntime2, toComponent) as List == [toRuntime, toMaster]
    }

    def "ignores missing master configuration"() {
        def fromComponent = Stub(ComponentIdentifier)
        def toComponent = Stub(ComponentResolveMetadata)
        def fromRuntime = Stub(ConfigurationMetadata)
        def toRuntime = Stub(ConfigurationMetadata)
        fromRuntime.name >> "runtime"
        toComponent.getConfiguration("runtime") >> toRuntime
        toComponent.getConfiguration("master") >> null
        toRuntime.hierarchy >> ["compile", "runtime"]

        def dep = mavenDependencyMetadata(MavenScope.Compile, false, Stub(ModuleComponentSelector), [])

        expect:
        dep.selectLegacyConfigurations(fromComponent, fromRuntime, toComponent) as List == [toRuntime]
    }

    def "ignores empty master configuration"() {
        def fromComponent = Stub(ComponentIdentifier)
        def toComponent = Stub(ComponentResolveMetadata)
        def fromRuntime = Stub(ConfigurationMetadata)
        def toRuntime = Stub(ConfigurationMetadata)
        def toMaster = Stub(ConfigurationMetadata)
        fromRuntime.name >> "runtime"
        toComponent.getConfiguration("runtime") >> toRuntime
        toComponent.getConfiguration("master") >> toMaster
        toRuntime.hierarchy >> ["compile", "runtime"]

        def dep = mavenDependencyMetadata(MavenScope.Compile, false, Stub(ModuleComponentSelector), [])

        expect:
        dep.selectLegacyConfigurations(fromComponent, fromRuntime, toComponent) as List == [toRuntime]
    }

    def "falls back to default configuration when compile is not defined in target component"() {
        def fromComponent = Stub(ComponentIdentifier)
        def toComponent = Stub(ComponentResolveMetadata)
        def fromCompile = Stub(ConfigurationMetadata)
        def toDefault = Stub(ConfigurationMetadata)
        def toMaster = Stub(ConfigurationMetadata)
        fromCompile.name >> "compile"
        toComponent.getConfiguration("compile") >> null
        toComponent.getConfiguration("default") >> toDefault
        toComponent.getConfiguration("master") >> toMaster
        toMaster.artifacts >> [Stub(ComponentArtifactMetadata)]

        def dep = mavenDependencyMetadata(MavenScope.Compile, false, Stub(ModuleComponentSelector), [])

        expect:
        dep.selectLegacyConfigurations(fromComponent, fromCompile, toComponent) as List == [toDefault, toMaster]
    }

    def "falls back to default configuration when runtime is not defined in target component"() {
        def fromComponent = Stub(ComponentIdentifier)
        def toComponent = Stub(ComponentResolveMetadata)
        def fromRuntime = Stub(ConfigurationMetadata)
        def toDefault = Stub(ConfigurationMetadata)
        def toMaster = Stub(ConfigurationMetadata)
        fromRuntime.name >> "runtime"
        toComponent.getConfiguration("runtime") >> null
        toComponent.getConfiguration("default") >> toDefault
        toComponent.getConfiguration("master") >> toMaster
        toDefault.hierarchy >> ["compile", "default"]
        toMaster.artifacts >> [Stub(ComponentArtifactMetadata)]

        def dep = mavenDependencyMetadata(MavenScope.Compile, false, Stub(ModuleComponentSelector), [])

        expect:
        dep.selectLegacyConfigurations(fromComponent, fromRuntime, toComponent) as List == [toDefault, toMaster]
    }

    def "fails when compile configuration is not defined in target component"() {
        def fromComponent = Stub(ComponentIdentifier)
        def toComponent = Stub(ComponentResolveMetadata)
        def fromCompile = Stub(ConfigurationMetadata)
        fromCompile.name >> "compile"
        toComponent.getConfiguration("compile") >> null
        toComponent.getConfiguration("default") >> null
        toComponent.getConfiguration("master") >> null

        def dep = mavenDependencyMetadata(MavenScope.Compile, false, Stub(ModuleComponentSelector), [])

        when:
        dep.selectLegacyConfigurations(fromComponent, fromCompile, toComponent)

        then:
        thrown(ConfigurationNotFoundException)
    }

    def "fails when runtime configuration is not defined in target component"() {
        def fromComponent = Stub(ComponentIdentifier)
        def toComponent = Stub(ComponentResolveMetadata)
        def fromRuntime = Stub(ConfigurationMetadata)
        fromRuntime.name >> "runtime"
        toComponent.getConfiguration("runtime") >> null
        toComponent.getConfiguration("default") >> null
        toComponent.getConfiguration("master") >> null

        def dep = mavenDependencyMetadata(MavenScope.Compile, false, Stub(ModuleComponentSelector), [])

        when:
        dep.selectLegacyConfigurations(fromComponent, fromRuntime, toComponent)

        then:
        thrown(ConfigurationNotFoundException)
    }

    private static MavenDependencyDescriptor mavenDependencyMetadata(MavenScope scope, boolean optional, ModuleComponentSelector selector, List<ExcludeMetadata> excludes) {
        return new MavenDependencyDescriptor(scope, optional, selector, null, excludes)
    }
}
