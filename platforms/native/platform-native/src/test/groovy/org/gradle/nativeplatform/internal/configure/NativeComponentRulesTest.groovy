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
import org.gradle.api.internal.file.TestFiles
import org.gradle.model.ModelMap
import org.gradle.model.internal.core.ModelPath
import org.gradle.model.internal.core.MutableModelNode
import org.gradle.model.internal.manage.instance.ManagedInstance
import org.gradle.nativeplatform.BuildType
import org.gradle.nativeplatform.Flavor
import org.gradle.nativeplatform.NativeLibrarySpec
import org.gradle.nativeplatform.internal.TargetedNativeComponentInternal
import org.gradle.nativeplatform.internal.resolve.NativeDependencyResolver
import org.gradle.nativeplatform.platform.NativePlatform
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal
import org.gradle.nativeplatform.platform.internal.NativePlatforms
import org.gradle.platform.base.BinarySpec
import org.gradle.platform.base.internal.DefaultPlatformRequirement
import org.gradle.platform.base.internal.PlatformResolvers
import spock.lang.Specification

class NativeComponentRulesTest extends Specification {
    def platforms = Mock(PlatformResolvers)
    def nativePlatforms = Stub(NativePlatforms)
    def nativeDependencyResolver = Mock(NativeDependencyResolver)

    def platformRequirement = requirement("platform1")
    def platform = createStub(NativePlatformInternal, "platform1")
    def buildType = createStub(BuildType, "buildType1")
    def flavor = createStub(Flavor, "flavor1")

    MockNativeLibrarySpec component
    def createdBinaries = [] as SortedSet

    static interface MockNativeLibrarySpec extends TargetedNativeComponentInternal, NativeLibrarySpec {}
    static interface MockBinaries extends ModelMap<BinarySpec>, ManagedInstance {}

    def setup() {
        def backingNode = Mock(MutableModelNode) {
            getPath() >> { ModelPath.path("test") }
        }
        def mockBinaries
        mockBinaries = Mock(MockBinaries) {
            withType(_) >> { return mockBinaries }
            getBackingNode() >> backingNode
            create(_, _) >> { String name, Class<?> type ->
                createdBinaries << name
            }
        }
        component = Mock(MockNativeLibrarySpec) {
            getName() >> "name"
            getBinaries() >> mockBinaries
            getTargetPlatforms() >> []
            chooseBuildTypes(_) >> { Set<? extends BuildType> buildTypes -> buildTypes }
            chooseFlavors(_) >> { Set<? extends Flavor> flavors -> flavors }
        }
    }

    def "does not use variant dimension names for single valued dimensions"() {
        when:
        NativeComponentRules.createBinariesImpl(component, platforms, [buildType].toSet(), [flavor].toSet(), nativePlatforms, nativeDependencyResolver, TestFiles.fileCollectionFactory())

        then:
        _ * component.targetPlatforms >> [platformRequirement]
        1 * platforms.resolve(NativePlatform, requirement("platform1")) >> platform
        createdBinaries == ([
            "sharedLibrary",
            "staticLibrary",
        ] as SortedSet)
    }

    def "does not use variant dimension names when component targets a single point on dimension"() {
        when:
        NativeComponentRules.createBinariesImpl(component, platforms, [buildType].toSet(), [flavor].toSet(), nativePlatforms, nativeDependencyResolver, TestFiles.fileCollectionFactory())

        then:
        _ * component.targetPlatforms >> [platformRequirement]
        _ * component.chooseBuildTypes(_) >> [buildType]
        _ * component.chooseFlavors(_) >> [flavor]
        1 * platforms.resolve(NativePlatform, requirement("platform1")) >> platform
        createdBinaries == ([
            "sharedLibrary",
            "staticLibrary",
        ] as SortedSet)
    }

    def "includes platform in name for when multiple platforms"() {
        def platform2 = createStub(NativePlatformInternal, "platform2")

        when:
        NativeComponentRules.createBinariesImpl(component, platforms, [buildType].toSet(), [flavor].toSet(), nativePlatforms, nativeDependencyResolver, TestFiles.fileCollectionFactory())

        then:
        _ * component.targetPlatforms >> [requirement("platform1"), requirement("platform2")]
        1 * platforms.resolve(NativePlatform, requirement("platform1")) >> platform
        1 * platforms.resolve(NativePlatform, requirement("platform2")) >> platform2

        then:
        createdBinaries == ([
            "platform1SharedLibrary",
            "platform1StaticLibrary",
            "platform2SharedLibrary",
            "platform2StaticLibrary",
        ] as SortedSet)
    }

    def "includes buildType in name for when multiple buildTypes"() {
        final BuildType buildType2 = createStub(BuildType, "buildType2")

        when:
        NativeComponentRules.createBinariesImpl(component, platforms, [buildType, buildType2].toSet(), [flavor].toSet(), nativePlatforms, nativeDependencyResolver, TestFiles.fileCollectionFactory())

        then:
        _ * component.targetPlatforms >> [requirement("platform1")]
        1 * platforms.resolve(NativePlatform, requirement("platform1")) >> platform
        createdBinaries == ([
            "buildType1SharedLibrary",
            "buildType1StaticLibrary",
            "buildType2SharedLibrary",
            "buildType2StaticLibrary",
        ] as SortedSet)
    }

    def "includes flavor in name for when multiple flavors"() {
        final Flavor flavor2 = createStub(Flavor, "flavor2")

        when:
        NativeComponentRules.createBinariesImpl(component, platforms, [buildType].toSet(), [flavor, flavor2].toSet(), nativePlatforms, nativeDependencyResolver, TestFiles.fileCollectionFactory())

        then:
        _ * component.targetPlatforms >> [requirement("platform1")]
        1 * platforms.resolve(NativePlatform, requirement("platform1")) >> platform
        createdBinaries == ([
            "flavor1SharedLibrary",
            "flavor1StaticLibrary",
            "flavor2SharedLibrary",
            "flavor2StaticLibrary",
        ] as SortedSet)
    }

    def requirement(String name) {
        DefaultPlatformRequirement.create(name)
    }

    private <T extends Named> T createStub(Class<T> type, def name) {
        def stub = Stub(type) {
            getName() >> name
        }
        return stub
    }
}
