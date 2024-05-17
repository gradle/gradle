/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.testkit

import com.google.common.math.IntMath
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.testkit.runner.GradleRunner

class TestKitDependencyClassVisibilityIntegrationTest extends AbstractIntegrationSpec {

    def "test kit dependency is not implicitly put on the test compile classpath"() {
        when:
        buildScript """
            plugins { id "org.gradle.java" }
        """

        file("src/test/java/Test.java") << """
            import $GradleRunner.name;
            class Test {}
        """

        then:
        fails 'build'
        failure.assertHasErrorOutput "package ${GradleRunner.package.name} does not exist"
    }

    def "gradle implementation dependencies are not visible to gradleTestKit() users"() {
        when:
        buildScript """
            plugins { id "org.gradle.java" }
            dependencies { testImplementation gradleTestKit() }
        """

        file("src/test/java/Test.java") << """
            import $IntMath.name;
            class Test {}
        """

        then:
        fails 'testClasses'
        failure.assertHasErrorOutput "package ${IntMath.package.name} does not exist"
    }

    def "gradle implementation dependencies do not conflict with user classes"() {
        when:
        buildScript """
            plugins { id "org.gradle.java" }
            ${mavenCentralRepository()}
            dependencies {
                testImplementation gradleTestKit()
                testImplementation 'com.google.guava:guava-jdk5:13.0'
            }
        """

        file("src/test/java/Test.java") << """
            import $IntMath.name;
            class Test {}
        """

        then:
        succeeds 'testClasses'
    }

}
