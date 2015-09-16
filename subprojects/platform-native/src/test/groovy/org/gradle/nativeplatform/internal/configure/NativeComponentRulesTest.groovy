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
import org.gradle.model.internal.core.DefaultInstanceFactoryRegistry
import org.gradle.model.internal.core.DefaultNodeInitializerRegistry
import org.gradle.model.internal.core.ModelReference
import org.gradle.model.internal.fixture.ModelRegistryHelper
import org.gradle.model.internal.manage.schema.extract.DefaultConstructableTypesRegistry
import org.gradle.model.internal.manage.schema.extract.DefaultModelSchemaStore
import org.gradle.model.internal.type.ModelType
import org.gradle.nativeplatform.*
import org.gradle.nativeplatform.internal.DefaultNativeLibrarySpec
import org.gradle.nativeplatform.internal.resolve.NativeDependencyResolver
import org.gradle.nativeplatform.platform.NativePlatform
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal
import org.gradle.nativeplatform.platform.internal.NativePlatforms
import org.gradle.platform.base.component.BaseComponentFixtures
import org.gradle.platform.base.internal.*
import spock.lang.Specification

class NativeComponentRulesTest extends Specification {
    def instantiator = DirectInstantiator.INSTANCE
    def namingSchemeBuilder = Spy(DefaultBinaryNamingSchemeBuilder)
    def platforms = Mock(PlatformResolvers)
    def nativePlatforms = Stub(NativePlatforms)
    def nativeDependencyResolver = Mock(NativeDependencyResolver)
    def platform = createStub(NativePlatformInternal, "platform1")

    def buildType = createStub(BuildType, "buildType1")
    def flavor = createStub(Flavor, "flavor1")

    def id = new DefaultComponentSpecIdentifier("project", "name")
    def modelRegistry = new ModelRegistryHelper();
    def component

    def setup() {
        def instanceFactoryRegistry = new DefaultInstanceFactoryRegistry()
        [StaticLibraryBinarySpec, SharedLibraryBinarySpec, NativeExecutableBinarySpec].each { type ->
            instanceFactoryRegistry.register(ModelType.of(type), ModelReference.of(BinarySpecFactory))
        }
        def nodeInitializerRegistry = new DefaultNodeInitializerRegistry(DefaultModelSchemaStore.instance, instanceFactoryRegistry, [], new DefaultConstructableTypesRegistry())
        component = BaseComponentFixtures.create(DefaultNativeLibrarySpec.class, modelRegistry, id, Stub(ProjectSourceSet), instantiator, nodeInitializerRegistry)
    }

    def "does not use variant dimension names for single valued dimensions"() {
        component.targetPlatform("platform1")

        when:
        NativeComponentRules.createBinariesImpl(component, platforms, [buildType].toSet(), [flavor].toSet(), nativePlatforms, nativeDependencyResolver, namingSchemeBuilder)

        then:
        1 * platforms.resolve(NativePlatform, requirement("platform1")) >> platform
        1 * namingSchemeBuilder.withComponentName("name") >> namingSchemeBuilder
        0 * namingSchemeBuilder.withVariantDimension(_)
    }

    def "does not use variant dimension names when component targets a single point on dimension"() {
        when:
        component.targetPlatform("platform1")
        component.targetBuildTypes("buildType1")
        component.targetFlavors("flavor1")
        NativeComponentRules.createBinariesImpl(component, platforms, [buildType].toSet(), [flavor].toSet(), nativePlatforms, nativeDependencyResolver, namingSchemeBuilder)

        then:
        1 * platforms.resolve(NativePlatform, requirement("platform1")) >> platform
        component.binaries.keySet() == [
            "nameSharedLibrary",
            "nameStaticLibrary",
        ].toSet()
    }

    def "includes platform in name for when multiple platforms"() {
        def platform2 = createStub(NativePlatformInternal, "platform2")
        component.targetPlatform("platform1")
        component.targetPlatform("platform2")

        when:
        NativeComponentRules.createBinariesImpl(component, platforms, [buildType].toSet(), [flavor].toSet(), nativePlatforms, nativeDependencyResolver, namingSchemeBuilder)

        then:
        1 * platforms.resolve(NativePlatform, requirement("platform1")) >> platform
        1 * platforms.resolve(NativePlatform, requirement("platform2")) >> platform2

        then:
        component.binaries.keySet() == [
            "platform1NameStaticLibrary",
            "platform1NameSharedLibrary",
            "platform2NameStaticLibrary",
            "platform2NameSharedLibrary",
        ].toSet()
    }

    def "includes buildType in name for when multiple buildTypes"() {
        final BuildType buildType2 = createStub(BuildType, "buildType2")
        component.targetPlatform("platform1")

        when:
        NativeComponentRules.createBinariesImpl(component, platforms, [buildType, buildType2].toSet(), [flavor].toSet(), nativePlatforms, nativeDependencyResolver, namingSchemeBuilder)

        then:
        1 * platforms.resolve(NativePlatform, requirement("platform1")) >> platform
        component.binaries.keySet() == [
            "buildType1NameSharedLibrary",
            "buildType1NameStaticLibrary",
            "buildType2NameSharedLibrary",
            "buildType2NameStaticLibrary",
        ].toSet()
    }

    def "includes flavor in name for when multiple flavors"() {
        component.targetPlatform("platform1")
        final Flavor flavor2 = createStub(Flavor, "flavor2")

        when:
        NativeComponentRules.createBinariesImpl(component, platforms, [buildType].toSet(), [flavor, flavor2].toSet(), nativePlatforms, nativeDependencyResolver, namingSchemeBuilder)

        then:
        1 * platforms.resolve(NativePlatform, requirement("platform1")) >> platform
        component.binaries.keySet() == [
            "flavor1NameSharedLibrary",
            "flavor1NameStaticLibrary",
            "flavor2NameSharedLibrary",
            "flavor2NameStaticLibrary",
        ].toSet()
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
