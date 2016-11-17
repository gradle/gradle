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

public class AARFilterExternalIntegrationTest extends AbstractAARFilterAndTransformIntegrationTest {

    def enabledFeatures() {
        [Feature.FILTER_EXTERNAL]
    }

    def "processJar includes jar from published java module"() {
        when:
        dependency "project(':java-lib')"
        dependency "project(':android-lib')"
        dependency "'org.gradle:ext-java-lib:1.0'"
        dependency "'org.gradle:ext-android-lib:1.0'"

        then:
        artifacts('processJar') == [
            '/maven-repo/org/gradle/ext-java-lib/1.0/ext-java-lib-1.0.jar'
        ]
    }
}
