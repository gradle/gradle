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

package org.gradle.nativeplatform.internal.configure

import org.gradle.api.Named
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.language.base.ProjectSourceSet
import org.gradle.language.base.internal.DefaultFunctionalSourceSet
import org.gradle.nativeplatform.BuildType
import org.gradle.nativeplatform.Flavor
import org.gradle.nativeplatform.internal.DefaultNativeExecutableSpec
import org.gradle.nativeplatform.platform.NativePlatform
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal
import org.gradle.platform.base.PlatformContainer
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainRegistryInternal
import org.gradle.platform.base.component.BaseComponentSpec
import org.gradle.platform.base.internal.BinaryNamingSchemeBuilder
import org.gradle.platform.base.internal.DefaultComponentSpecIdentifier
import spock.lang.Specification

class NativeComponentSpecInitializerTest extends Specification {
    def instantiator = new DirectInstantiator()
    def toolChains = Mock(NativeToolChainRegistryInternal)
    def toolChain = Mock(NativeToolChainInternal)
    def toolProvider = Mock(PlatformToolProvider)
    def nativeBinariesFactory = Mock(NativeBinariesFactory)
    def namingSchemeBuilder = Mock(BinaryNamingSchemeBuilder)
    def platform = createStub(NativePlatformInternal, "platform1")

    def buildType = createStub(BuildType, "buildType1")
    def flavor = createStub(Flavor, "flavor1")

    def id = new DefaultComponentSpecIdentifier("project", "name")
    def mainSourceSet = new DefaultFunctionalSourceSet("testFSS", new DirectInstantiator(), Stub(ProjectSourceSet));
    def component = BaseComponentSpec.create(DefaultNativeExecutableSpec, id, mainSourceSet, instantiator)

    def "does not use variant dimension names for single valued dimensions"() {
        def platforms = Mock(PlatformContainer)
        platforms.add(platform)
        component.targetPlatforms("platform1")

        when:
        def factory = new NativeComponentSpecInitializer(nativeBinariesFactory, namingSchemeBuilder, toolChains, platforms, [buildType], [flavor])
        factory.execute(component)

        then:
        1 * platforms.chooseFromTargets(NativePlatform, ["platform1"]) >> [ platform ]
        1 * toolChains.getForPlatform(platform) >> toolChain
        1 * toolChain.select(platform) >> toolProvider
        1 * namingSchemeBuilder.withComponentName("name") >> namingSchemeBuilder
        1 * nativeBinariesFactory.createNativeBinaries(component, namingSchemeBuilder, toolChain, toolProvider, platform, buildType, flavor)
        0 * namingSchemeBuilder._
    }

    def "does not use variant dimension names when component targets a single point on dimension"() {
        def platforms = Mock(PlatformContainer)
        def platform2 = createStub(NativePlatformInternal, "platform2")
        platforms.add(platform)
        platforms.add(platform2)

        when:
        def factory = new NativeComponentSpecInitializer(nativeBinariesFactory, namingSchemeBuilder, toolChains,
                platforms, [buildType, Mock(BuildType)], [flavor, Mock(Flavor)])
        component.targetPlatforms("platform1")
        component.targetBuildTypes("buildType1")
        component.targetFlavors("flavor1")
        factory.execute(component)

        then:
        1 * platforms.chooseFromTargets(NativePlatform, ["platform1"]) >> [ platform ]
        1 * toolChains.getForPlatform(platform) >> toolChain
        1 * toolChain.select(platform) >> toolProvider
        1 * namingSchemeBuilder.withComponentName("name") >> namingSchemeBuilder
        1 * nativeBinariesFactory.createNativeBinaries(component, namingSchemeBuilder, toolChain, toolProvider, platform, buildType, flavor)
        0 * namingSchemeBuilder._
    }

    def "includes platform in name for when multiple platforms"() {
        def platforms = Mock(PlatformContainer)
        def platform2 = createStub(NativePlatformInternal, "platform2")
        platforms.addAll([platform, platform2])
        component.targetPlatform("platform1", "platform2")

        when:
        def factory = new NativeComponentSpecInitializer(nativeBinariesFactory, namingSchemeBuilder, toolChains,
                platforms, [buildType], [flavor])
        factory.execute(component)


        then:
        1 * platforms.chooseFromTargets(NativePlatform, ["platform1", "platform2"]) >> [ platform, platform2 ]
        then:
        1 * toolChains.getForPlatform(platform) >> toolChain
        1 * toolChain.select(platform) >> toolProvider
        1 * namingSchemeBuilder.withComponentName("name") >> namingSchemeBuilder
        1 * namingSchemeBuilder.withVariantDimension("platform1") >> namingSchemeBuilder
        1 * nativeBinariesFactory.createNativeBinaries(component, namingSchemeBuilder, toolChain, toolProvider, platform, buildType, flavor)
        0 * _

        then:
        1 * toolChains.getForPlatform(platform2) >> toolChain
        1 * toolChain.select(platform2) >> toolProvider
        1 * namingSchemeBuilder.withComponentName("name") >> namingSchemeBuilder
        1 * namingSchemeBuilder.withVariantDimension("platform2") >> namingSchemeBuilder
        1 * nativeBinariesFactory.createNativeBinaries(component, namingSchemeBuilder, toolChain, toolProvider, platform2, buildType, flavor)
        0 * _
    }

    def "includes buildType in name for when multiple buildTypes"() {
        final BuildType buildType2 = createStub(BuildType, "buildType2")
        def platforms = Mock(PlatformContainer)
        platforms.add(platform)
        component.targetPlatforms("platform1")

        when:
        def factory = new NativeComponentSpecInitializer(nativeBinariesFactory, namingSchemeBuilder, toolChains,
                platforms, [buildType, buildType2], [flavor])
        factory.execute(component)

        then:
        1 * platforms.chooseFromTargets(NativePlatform, ["platform1"]) >> [platform]
        1 * toolChains.getForPlatform(platform) >> toolChain
        1 * toolChain.select(platform) >> toolProvider
        1 * namingSchemeBuilder.withComponentName("name") >> namingSchemeBuilder

        then:
        1 * namingSchemeBuilder.withVariantDimension("buildType1") >> namingSchemeBuilder
        1 * nativeBinariesFactory.createNativeBinaries(component, namingSchemeBuilder, toolChain, toolProvider, platform, buildType, flavor)
        0 * _

        then:
        1 * namingSchemeBuilder.withVariantDimension("buildType2") >> namingSchemeBuilder
        1 * nativeBinariesFactory.createNativeBinaries(component, namingSchemeBuilder, toolChain, toolProvider, platform, buildType2, flavor)
        0 * _
    }

    def "includes flavor in name for when multiple flavors"() {
        def platforms = Mock(PlatformContainer)
        platforms.add(platform)
        component.targetPlatforms("platform1")
        final Flavor flavor2 = createStub(Flavor, "flavor2")
        when:
        def factory = new NativeComponentSpecInitializer(nativeBinariesFactory, namingSchemeBuilder, toolChains,
                platforms, [buildType], [flavor, flavor2])
        factory.execute(component)

        then:
        1 * platforms.chooseFromTargets(NativePlatform, ["platform1"]) >> [platform]
        1 * toolChains.getForPlatform(platform) >> toolChain
        1 * toolChain.select(platform) >> toolProvider
        1 * namingSchemeBuilder.withComponentName("name") >> namingSchemeBuilder

        then:
        1 * namingSchemeBuilder.withVariantDimension("flavor1") >> namingSchemeBuilder
        1 * nativeBinariesFactory.createNativeBinaries(component, namingSchemeBuilder, toolChain, toolProvider, platform, buildType, flavor)
        0 * _

        then:
        1 * namingSchemeBuilder.withVariantDimension("flavor2") >> namingSchemeBuilder
        1 * nativeBinariesFactory.createNativeBinaries(component, namingSchemeBuilder, toolChain, toolProvider, platform, buildType, flavor2)
        0 * _
    }

    private <T extends Named> T createStub(Class<T> type, def name) {
        def stub = Stub(type) {
            getName() >> name
        }
        return stub
    }
}
