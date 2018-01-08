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
import spock.lang.Specification


class DefaultLibraryDependenciesTest extends Specification {
    def "can add api dependency"() {
        def configurations = Stub(ConfigurationContainer)
        def dependencyFactory = Mock(DependencyHandler)
        def apiDeps = Mock(Configuration)
        def deps = Mock(DependencySet)
        def dep = Stub(Dependency)

        given:
        configurations.create("api") >> apiDeps
        apiDeps.dependencies >> deps

        def dependencies = new DefaultLibraryDependencies(configurations, "impl", "api") {
            @Override
            protected DependencyHandler getDependencyHandler() {
                return dependencyFactory
            }
        }

        when:
        dependencies.api("a:b:c")

        then:
        1 * dependencyFactory.create("a:b:c") >> dep
        1 * deps.add(dep)
    }

}
