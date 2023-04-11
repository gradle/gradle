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

package org.gradle.language.swift.internal

import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolvableDependencies
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal
import org.gradle.api.internal.artifacts.configurations.ConfigurationRolesForMigration
import org.gradle.api.internal.artifacts.configurations.RoleBasedConfigurationContainerInternal
import org.gradle.api.provider.Provider
import org.gradle.language.cpp.internal.NativeVariantIdentity
import org.gradle.language.nativeplatform.internal.Names
import org.gradle.language.swift.SwiftPlatform
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.gradle.util.UsesNativeServices
import org.junit.Rule
import spock.lang.Specification

@UsesNativeServices
class DefaultSwiftBinaryTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    def project = TestUtil.createRootProject(tmpDir.testDirectory)
    def implementation = Stub(ConfigurationInternal)
    def compile = Stub(Configuration)
    def link = Stub(Configuration)
    def runtime = Stub(Configuration)
    def configurations = Stub(RoleBasedConfigurationContainerInternal)
    def incoming = Mock(ResolvableDependencies)
    DefaultSwiftBinary binary

    def setup() {
        _ * configurations.resolvableBucket("swiftCompileDebug") >> compile
        _ * configurations.resolvableBucket("nativeLinkDebug") >> link
        _ * configurations.createWithRole('nativeRuntimeDebug', ConfigurationRolesForMigration.INTENDED_RESOLVABLE_BUCKET_TO_INTENDED_RESOLVABLE) >> runtime

        binary = new DefaultSwiftBinary(Names.of("mainDebug"), project.objects, project.taskDependencyFactory, Stub(Provider), false, Stub(FileCollection), configurations, implementation, Stub(SwiftPlatform), Stub(NativeToolChainInternal), Stub(PlatformToolProvider), Stub(NativeVariantIdentity))
    }

    def "compileModules is a transformed view of compile"() {
        given:
        compile.incoming >> incoming

        when:
        binary.compileModules.files

        then:
        1 * incoming.artifacts >> Stub(ArtifactCollection)
    }

    def "creates configurations for the binary" () {
        expect:
        binary.linkLibraries == link
        binary.runtimeLibraries == runtime
    }

}
