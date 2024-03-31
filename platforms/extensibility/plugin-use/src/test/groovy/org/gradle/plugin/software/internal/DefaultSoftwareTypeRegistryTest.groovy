/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.plugin.software.internal

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.SoftwareType
import spock.lang.Specification

class DefaultSoftwareTypeRegistryTest extends Specification {
    def registry = new DefaultSoftwareTypeRegistry()

    def "can register and retrieve a software type"() {
        when:
        registry.register(SoftwareTypeImpl)

        then:
        def implementations = registry.softwareTypeImplementations
        implementations.size() == 1
        implementations[0].modelPublicType == TestModel
        implementations[0].softwareType == "test"
    }

    def "cannot register a plugin that is not a software type"() {
        when:
        registry.register(NotASoftwareTypeImpl)

        then:
        def implementations = registry.softwareTypeImplementations
        implementations.isEmpty()
    }

    def "registering the same plugin twice does not add two implementations"() {
        when:
        registry.register(SoftwareTypeImpl)
        registry.register(SoftwareTypeImpl)

        then:
        def implementations = registry.softwareTypeImplementations
        implementations.size() == 1
    }

    def "cannot register two plugins with the same software type"() {
        when:
        registry.register(SoftwareTypeImpl)
        registry.register(DuplicateSoftwareTypeImpl)
        registry.getSoftwareTypeImplementations()

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Software type 'test' is registered by both '${this.class.name}\$DuplicateSoftwareTypeImpl' and '${this.class.name}\$SoftwareTypeImpl'"
    }

    private static class TestModel { }

    private abstract static class SoftwareTypeImpl implements Plugin<Project> {
        @SoftwareType(name = "test", modelPublicType = TestModel)
        abstract TestModel getModel()

        @Override
        void apply(Project project) {
        }
    }

    private abstract static class NotASoftwareTypeImpl implements Plugin<Project> {
        abstract TestModel getModel()

        @Override
        void apply(Project project) {
        }
    }

    private abstract static class DuplicateSoftwareTypeImpl implements Plugin<Project> {
        @SoftwareType(name = "test", modelPublicType = TestModel)
        abstract TestModel getModel()

        @Override
        void apply(Project project) {
        }
    }
}
