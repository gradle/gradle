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
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.file.ProjectLayout
import org.gradle.api.provider.Provider
import org.gradle.nativeplatform.platform.NativePlatform
import org.gradle.nativeplatform.toolchain.NativeToolChain
import spock.lang.Specification


class DefaultNativeBinaryTest extends Specification {
    ConfigurationContainer configurations = Mock(ConfigurationContainer)
    DependencyHandler dependencyFactory = Mock(DependencyHandler)
    Configuration implementation = Stub(Configuration)

    def "has implementation dependencies"() {
        def implDeps = Mock(Configuration)

        when:
        def binary = new TestBinary("binary", Stub(ProjectLayout), configurations, implementation)

        then:
        1 * configurations.create("binaryImplementation") >> implDeps
        1 * implDeps.extendsFrom(implementation)

        expect:
        binary.implementationDependencies == implDeps
    }

    def "can add implementation dependency"() {
        def implDeps = Mock(Configuration)
        def deps = Mock(DependencySet)
        def dep = Stub(Dependency)

        given:
        configurations.create("binaryImplementation") >> implDeps
        implDeps.dependencies >> deps

        def binary = new TestBinary("binary", Stub(ProjectLayout), configurations, implementation)

        when:
        binary.dependencies.implementation("a:b:c")

        then:
        1 * dependencyFactory.create("a:b:c") >> dep
        1 * deps.add(dep)
    }

    class TestBinary extends DefaultNativeBinary {
        TestBinary(String name, ProjectLayout projectLayout, ConfigurationContainer configurations, Configuration componentImplementation) {
            super(name, projectLayout, configurations, componentImplementation)
        }

        @Override
        protected DependencyHandler getDependencyHandler() {
            return dependencyFactory
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
        NativePlatform getTargetPlatform() {
            throw new UnsupportedOperationException()
        }

        @Override
        NativeToolChain getToolChain() {
            throw new UnsupportedOperationException()
        }
    }
}
