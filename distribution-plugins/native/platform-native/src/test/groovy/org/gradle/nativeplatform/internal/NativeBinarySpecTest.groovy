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

package org.gradle.nativeplatform.internal

import org.gradle.api.internal.CollectionCallbackActionDecorator
import org.gradle.language.nativeplatform.DependentSourceSet
import org.gradle.model.internal.core.MutableModelNode
import org.gradle.nativeplatform.BuildType
import org.gradle.nativeplatform.Flavor
import org.gradle.nativeplatform.NativeBinarySpec
import org.gradle.nativeplatform.NativeDependencySet
import org.gradle.nativeplatform.NativeLibraryBinary
import org.gradle.nativeplatform.NativeLibrarySpec
import org.gradle.nativeplatform.internal.resolve.NativeBinaryResolveResult
import org.gradle.nativeplatform.internal.resolve.NativeDependencyResolver
import org.gradle.nativeplatform.platform.NativePlatform
import org.gradle.nativeplatform.platform.internal.Architectures
import org.gradle.nativeplatform.tasks.ObjectFilesToBinary
import org.gradle.platform.base.component.BaseComponentFixtures
import org.gradle.platform.base.internal.DefaultBinaryNamingScheme
import org.gradle.platform.base.internal.DefaultBinaryTasksCollection
import org.gradle.platform.base.internal.DefaultComponentSpecIdentifier
import spock.lang.Specification

class NativeBinarySpecTest extends Specification {
    def flavor1 = new DefaultFlavor("flavor1")
    def id = new DefaultComponentSpecIdentifier("project", "name")
    def component = BaseComponentFixtures.createNode(NativeLibrarySpec, TestNativeComponentSpec, id)

    def platform1 = Stub(NativePlatform) {
        getArchitecture() >> Architectures.forInput("i386")
    }
    def buildType1 = Stub(BuildType) {
        getName() >> "BuildType1"
    }
    def resolver = Mock(NativeDependencyResolver)

    def "uses resolver to resolve lib to dependency"() {
        def binary = testBinary(component, flavor1)
        def lib = new Object()
        def dependency = Stub(NativeDependencySet)

        when:
        binary.lib(lib)

        and:
        1 * resolver.resolve({ NativeBinaryResolveResult result ->
            result.allResolutions*.input == [lib]
        }) >> { NativeBinaryResolveResult result ->
            result.allResolutions[0].nativeDependencySet = dependency
        }

        then:
        binary.libs// == [dependency]
    }

    def "binary libs include source set dependencies"() {
        def binary = testBinary(component)
        def lib = new Object()
        def dependency = Stub(NativeDependencySet)

        when:
        def sourceSet = Stub(DependentSourceSet) {
            getLibs() >> [lib]
        }
        binary.inputs.add sourceSet

        1 * resolver.resolve({ NativeBinaryResolveResult result ->
            result.allResolutions*.input == [lib]
        }) >> { NativeBinaryResolveResult result ->
            result.allResolutions[0].nativeDependencySet = dependency
        }

        then:
        binary.getLibs(sourceSet) == [dependency]
    }

    def "order of libraries is maintained"() {
        def binary = testBinary(component)
        def libraryBinary = Mock(NativeLibraryBinary)
        def dependency1 = Stub(NativeDependencySet)
        def dependency2 = Stub(NativeDependencySet)
        def dependency3 = Stub(NativeDependencySet)

        when:
        binary.lib(dependency1)
        binary.lib(libraryBinary)
        binary.lib(dependency3)

        and:
        1 * resolver.resolve({ NativeBinaryResolveResult result ->
            result.allResolutions*.input == [dependency1, libraryBinary, dependency3]
        }) >> { NativeBinaryResolveResult result ->
            result.allResolutions[0].nativeDependencySet = dependency1
            result.allResolutions[1].nativeDependencySet = dependency2
            result.allResolutions[2].nativeDependencySet = dependency3
        }

        then:
        binary.libs as List == [dependency1, dependency2, dependency3]
    }

    def "library added to binary is ordered before library for source set"() {
        def binary = testBinary(component)
        def lib1 = new Object()
        def dep1 = Stub(NativeDependencySet)
        def lib2 = new Object()
        def dep2 = Stub(NativeDependencySet)
        def sourceLib = new Object()
        def sourceDep = Stub(NativeDependencySet)

        when:
        binary.lib(lib1)
        def sourceSet = Stub(DependentSourceSet) {
            getLibs() >> [sourceLib]
        }
        binary.inputs.add sourceSet
        binary.lib(lib2)

        and:
        1 * resolver.resolve({ NativeBinaryResolveResult result ->
            result.allResolutions*.input == [lib1, lib2, sourceLib]
        }) >> { NativeBinaryResolveResult result ->
            result.allResolutions[0].nativeDependencySet = dep1
            result.allResolutions[1].nativeDependencySet = dep2
            result.allResolutions[2].nativeDependencySet = sourceDep
        }

        then:
        binary.libs as List == [dep1, dep2, sourceDep]
    }

    def testBinary(MutableModelNode componentNode, Flavor flavor = new DefaultFlavor(DefaultFlavor.DEFAULT)) {
        TestNativeBinariesFactory.create(
            NativeBinarySpec, TestNativeBinarySpec, "test", componentNode,
            DefaultBinaryNamingScheme.component("baseName"), resolver, platform1, buildType1, flavor
        )
    }

    static class TestNativeComponentSpec extends DefaultNativeLibrarySpec {
    }

    static class TestNativeBinarySpec extends AbstractNativeBinarySpec {
        def owner
        def tasks = new DefaultBinaryTasksCollection(this, null, CollectionCallbackActionDecorator.NOOP)

        String getOutputFileName() {
            return null
        }

        File getPrimaryOutput() {
            File binaryOutputDir = getBinaryOutputDir();
            return new File(binaryOutputDir, getOutputFileName());
        }

        @Override
        protected ObjectFilesToBinary getCreateOrLink() {
            return null;
        }

        DefaultBinaryTasksCollection getTasks() {
            return tasks;
        }
    }
}
