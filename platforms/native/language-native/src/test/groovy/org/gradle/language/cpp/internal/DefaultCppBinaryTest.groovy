/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.language.cpp.internal

import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal
import org.gradle.api.internal.artifacts.configurations.RoleBasedConfigurationContainerInternal
import org.gradle.api.provider.Provider
import org.gradle.language.cpp.CppPlatform
import org.gradle.language.nativeplatform.internal.Names
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.gradle.util.UsesNativeServices
import org.junit.Rule
import spock.lang.Specification

@UsesNativeServices
class DefaultCppBinaryTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    def project = TestUtil.createRootProject(tmpDir.testDirectory)
    def implementation = Stub(ConfigurationInternal)
    def headerDirs = Stub(FileCollection)
    def compile = Stub(Configuration)
    def link = Stub(Configuration)
    def runtime = Stub(Configuration)
    def configurations = Stub(RoleBasedConfigurationContainerInternal)

    DefaultCppBinary binary

    def setup() {
        def componentHeaders = Stub(FileCollection)
        _ * configurations.create("cppCompileDebug") >> compile
        _ * configurations.create("nativeLinkDebug") >> link
        _ * configurations.create("nativeRuntimeDebug") >> runtime
        _ * componentHeaders.plus(_) >> headerDirs

        binary = new DefaultCppBinary(Names.of("mainDebug"), project.objects, Stub(Provider), Stub(FileCollection), componentHeaders, configurations, implementation, Stub(CppPlatform), Stub(NativeToolChainInternal), Stub(PlatformToolProvider), Stub(NativeVariantIdentity))
    }

    def "creates configurations for the binary"() {
        expect:
        binary.compileIncludePath == headerDirs
        binary.linkLibraries
        binary.runtimeLibraries
    }
}
