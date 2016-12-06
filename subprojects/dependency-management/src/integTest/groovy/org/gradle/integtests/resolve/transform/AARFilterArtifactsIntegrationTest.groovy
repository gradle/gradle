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

package org.gradle.integtests.resolve.transform;

class AARFilterArtifactsIntegrationTest extends AbstractAndroidFilterAndTransformIntegrationTest {

    def "jar artifacts are resolved for published java module and local java configuration"() {
        when:
        dependency "project(path: ':java-lib', configuration: 'runtime')"
        dependency "'org.gradle:ext-java-lib:1.0'"

        then:
        artifacts('jar') == [
            '/java-lib/build/libs/java-lib.jar',
            '/maven-repo/org/gradle/ext-java-lib/1.0/ext-java-lib-1.0.jar'
        ]
    }

    def "classes artifacts are resolved for local android and java libraries"() {
        when:
        dependency "project(':java-lib')"
        dependency "project(':android-lib')"

        then:
        artifacts('classes') == [
            '/java-lib/build/classes/main',
            '/android-lib/build/classes/main'
        ]
    }

}
