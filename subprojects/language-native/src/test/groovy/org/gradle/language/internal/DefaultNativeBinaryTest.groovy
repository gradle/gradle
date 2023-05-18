/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.language.internal

import org.gradle.api.artifacts.Configuration
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.language.nativeplatform.internal.Names
import org.gradle.nativeplatform.TargetMachine
import org.gradle.nativeplatform.toolchain.NativeToolChain
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.gradle.util.UsesNativeServices
import org.junit.Rule
import spock.lang.Specification

import javax.inject.Inject

@UsesNativeServices
class DefaultNativeBinaryTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    def project = TestUtil.createRootProject(tmpDir.testDirectory)
    Configuration implementation = Stub(ConfigurationInternal)

    def "has implementation dependencies"() {
        given:
        def binary = new TestBinary("binary", project.objects, implementation)

        expect:
        binary.implementationDependencies == project.configurations.binaryImplementation
        binary.implementationDependencies.extendsFrom == [implementation] as Set
    }

    def "can add implementation dependency"() {
        given:
        def binary = new TestBinary("binary", project.objects, implementation)
        binary.dependencies.implementation("a:b:c:")

        expect:
        binary.implementationDependencies.dependencies.size() == 1
    }

    class TestBinary extends DefaultNativeBinary {
        @Inject
        TestBinary(String name, ObjectFactory objectFactory, Configuration componentImplementation) {
            super(Names.of(name), objectFactory, componentImplementation)
        }

        @Override
        Provider<String> getBaseName() {
            throw new UnsupportedOperationException()
        }

        @Override
        boolean isDebuggable() {
            throw new UnsupportedOperationException()
        }

        @Override
        boolean isOptimized() {
            throw new UnsupportedOperationException()
        }

        @Override
        TargetMachine getTargetMachine() {
            throw new UnsupportedOperationException()
        }

        @Override
        NativeToolChain getToolChain() {
            throw new UnsupportedOperationException()
        }
    }
}
