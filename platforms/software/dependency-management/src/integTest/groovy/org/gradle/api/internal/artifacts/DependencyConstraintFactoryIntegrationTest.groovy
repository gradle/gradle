/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.artifacts

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

/**
 * Tests {@link org.gradle.api.artifacts.dsl.DependencyConstraintFactory}.
 */
class DependencyConstraintFactoryIntegrationTest extends AbstractIntegrationSpec {

    def "can create a module dependency constraint"() {
        given:
        buildFile << """
            abstract class Foo {
                @Inject abstract DependencyConstraintFactory getDependencyConstraintFactory()
            }
            def factory = objects.newInstance(Foo).getDependencyConstraintFactory()

            def dependency = factory.create("org", "foo", "1.0")
            assert dependency.group == "org"
            assert dependency.name == "foo"
            assert dependency.version == "1.0"
        """

        expect:
        succeeds("help")
    }

    def "can create project dependency constraint"() {
        given:
        settingsFile << """
            include("other")
        """
        file("other/build.gradle").touch()
        buildFile << """
            abstract class Foo {
                @Inject abstract DependencyConstraintFactory getDependencyConstraintFactory()
                @Inject abstract DependencyFactory getDependencyFactory()
            }
            def factory = objects.newInstance(Foo).getDependencyConstraintFactory()
            def dependencyFactory = objects.newInstance(Foo).getDependencyFactory()

            def dependency = factory.create(dependencyFactory.createProjectDependency(":other"))
            assert dependency.projectDependency.path == ":other"
        """

        expect:
        succeeds("help")
    }

}
