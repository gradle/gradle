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

package org.gradle.nativebinaries.internal.configure

import org.gradle.api.Named
import org.gradle.language.base.internal.BinaryNamingSchemeBuilder
import org.gradle.nativebinaries.BuildType
import org.gradle.nativebinaries.Flavor
import org.gradle.nativebinaries.internal.DefaultNativeExecutable
import org.gradle.nativebinaries.internal.ProjectNativeComponentIdentifier
import org.gradle.nativebinaries.platform.Platform
import org.gradle.nativebinaries.toolchain.internal.ToolChainInternal
import org.gradle.nativebinaries.toolchain.internal.ToolChainRegistryInternal
import spock.lang.Specification

class ProjectNativeComponentInitializerTest extends Specification {
    def toolChains = Mock(ToolChainRegistryInternal)
    def toolChain = Mock(ToolChainInternal)
    def nativeBinariesFactory = Mock(NativeBinariesFactory)
    def namingSchemeBuilder = Mock(BinaryNamingSchemeBuilder)

    def platform = createStub(Platform, "platform1")
    def buildType = createStub(BuildType, "buildType1")
    def flavor = createStub(Flavor, "flavor1")

    def id = new ProjectNativeComponentIdentifier("project", "name")
    def component = new DefaultNativeExecutable(id)

    def "does not use variant dimension names for single valued dimensions"() {
        when:
        def factory = new ProjectNativeComponentInitializer(nativeBinariesFactory, namingSchemeBuilder, toolChains, [platform], [buildType], [flavor])
        factory.execute(component)

        then:
        1 * toolChains.getForPlatform(platform) >> toolChain
        1 * namingSchemeBuilder.withComponentName("name") >> namingSchemeBuilder
        1 * nativeBinariesFactory.createNativeBinaries(component, namingSchemeBuilder, toolChain, platform, buildType, flavor)
        0 * namingSchemeBuilder._
    }

    def "does not use variant dimension names when component targets a single point on dimension"() {
        when:
        def factory = new ProjectNativeComponentInitializer(nativeBinariesFactory, namingSchemeBuilder, toolChains,
                [platform, Mock(Platform)], [buildType, Mock(BuildType)], [flavor, Mock(Flavor)])
        component.targetPlatforms("platform1")
        component.targetBuildTypes("buildType1")
        component.targetFlavors("flavor1")
        factory.execute(component)

        then:
        1 * toolChains.getForPlatform(platform) >> toolChain
        1 * namingSchemeBuilder.withComponentName("name") >> namingSchemeBuilder
        1 * nativeBinariesFactory.createNativeBinaries(component, namingSchemeBuilder, toolChain, platform, buildType, flavor)
        0 * namingSchemeBuilder._
    }

    def "includes platform in name for when multiple platforms"() {
        final Platform platform2 = createStub(Platform, "platform2")
        when:
        def factory = new ProjectNativeComponentInitializer(nativeBinariesFactory, namingSchemeBuilder, toolChains,
                [platform, platform2], [buildType], [flavor])
        factory.execute(component)

        then:
        1 * toolChains.getForPlatform(platform) >> toolChain
        1 * namingSchemeBuilder.withComponentName("name") >> namingSchemeBuilder
        1 * namingSchemeBuilder.withVariantDimension("platform1") >> namingSchemeBuilder
        1 * nativeBinariesFactory.createNativeBinaries(component, namingSchemeBuilder, toolChain, platform, buildType, flavor)
        0 * _

        then:
        1 * toolChains.getForPlatform(platform2) >> toolChain
        1 * namingSchemeBuilder.withComponentName("name") >> namingSchemeBuilder
        1 * namingSchemeBuilder.withVariantDimension("platform2") >> namingSchemeBuilder
        1 * nativeBinariesFactory.createNativeBinaries(component, namingSchemeBuilder, toolChain, platform2, buildType, flavor)
        0 * _
    }

    def "includes buildType in name for when multiple buildTypes"() {
        final BuildType buildType2 = createStub(BuildType, "buildType2")
        when:
        def factory = new ProjectNativeComponentInitializer(nativeBinariesFactory, namingSchemeBuilder, toolChains,
                [platform], [buildType, buildType2], [flavor])
        factory.execute(component)

        then:
        1 * toolChains.getForPlatform(platform) >> toolChain

        then:
        1 * namingSchemeBuilder.withComponentName("name") >> namingSchemeBuilder
        1 * namingSchemeBuilder.withVariantDimension("buildType1") >> namingSchemeBuilder
        1 * nativeBinariesFactory.createNativeBinaries(component, namingSchemeBuilder, toolChain, platform, buildType, flavor)
        0 * _

        then:
        1 * namingSchemeBuilder.withComponentName("name") >> namingSchemeBuilder
        1 * namingSchemeBuilder.withVariantDimension("buildType2") >> namingSchemeBuilder
        1 * nativeBinariesFactory.createNativeBinaries(component, namingSchemeBuilder, toolChain, platform, buildType2, flavor)
        0 * _
    }

    def "includes flavor in name for when multiple flavors"() {
        final Flavor flavor2 = createStub(Flavor, "flavor2")
        when:
        def factory = new ProjectNativeComponentInitializer(nativeBinariesFactory, namingSchemeBuilder, toolChains,
                [platform], [buildType], [flavor, flavor2])
        factory.execute(component)

        then:
        1 * toolChains.getForPlatform(platform) >> toolChain

        then:
        1 * namingSchemeBuilder.withComponentName("name") >> namingSchemeBuilder
        1 * namingSchemeBuilder.withVariantDimension("flavor1") >> namingSchemeBuilder
        1 * nativeBinariesFactory.createNativeBinaries(component, namingSchemeBuilder, toolChain, platform, buildType, flavor)
        0 * _

        then:
        1 * namingSchemeBuilder.withComponentName("name") >> namingSchemeBuilder
        1 * namingSchemeBuilder.withVariantDimension("flavor2") >> namingSchemeBuilder
        1 * nativeBinariesFactory.createNativeBinaries(component, namingSchemeBuilder, toolChain, platform, buildType, flavor2)
        0 * _
    }

    private <T extends Named> T createStub(Class<T> type, def name) {
        def stub = Stub(type) {
            getName() >> name
        }
        return stub
    }
}
