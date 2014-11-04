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
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.language.base.ProjectSourceSet
import org.gradle.language.base.internal.DefaultFunctionalSourceSet
import org.gradle.nativeplatform.BuildType
import org.gradle.nativeplatform.internal.configure.DefaultNativeBinariesFactory
import org.gradle.nativeplatform.internal.resolve.NativeDependencyResolver
import org.gradle.nativeplatform.platform.NativePlatform
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider
import org.gradle.platform.base.component.BaseComponentSpec
import org.gradle.platform.base.internal.DefaultBinaryNamingScheme
import org.gradle.platform.base.internal.DefaultComponentSpecIdentifier
import spock.lang.Specification

class DefaultNativeExecutableBinarySpecTest extends Specification {
    def instantiator = new DirectInstantiator()
    def namingScheme = new DefaultBinaryNamingScheme("bigOne", "executable", [])

    def "has useful string representation"() {
        given:
        def executable = BaseComponentSpec.create(DefaultNativeExecutableSpec, new DefaultComponentSpecIdentifier("path", "name"), new DefaultFunctionalSourceSet("name", instantiator, Stub(ProjectSourceSet)), instantiator)

        when:
        def binary = DefaultNativeBinariesFactory.create(DefaultNativeExecutableBinarySpec, instantiator, executable, namingScheme, Mock(NativeDependencyResolver), Stub(NativeToolChainInternal), Stub(PlatformToolProvider), Stub(NativePlatform), Stub(BuildType), new DefaultFlavor("flavorOne"))

        then:
        binary.toString() == "executable 'bigOne:executable'"
    }
}
