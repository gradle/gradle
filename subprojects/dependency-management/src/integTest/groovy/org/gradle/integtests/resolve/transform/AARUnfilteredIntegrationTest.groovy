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

package org.gradle.integtests.resolve.transform

import spock.lang.Unroll;

@Unroll
class AARUnfilteredIntegrationTest extends AbstractAndroidFilterAndTransformIntegrationTest {

    def "resolves class folder directly from local java library"() {
        when:
        dependency "project(':java-lib')"

        then:
        artifacts() == ['/java-lib/build/classes/main']
        executed ":java-lib:classes"
        notExecuted ':java-lib:jar'
    }

    def "resolves classes folder directly from local android library"() {
        when:
        dependency "project(':android-lib')"

        then:
        // Currently selects a single variant, should fail instead
        artifacts() == ['/android-lib/build/classes/main']
        executed ":android-lib:classes"
        notExecuted ":android-lib:jar"
        notExecuted ":android-lib:aar"
    }

    def "resolves jar from local java library via runtime configuration"() {
        when:
        dependency "project(path: ':java-lib', configuration: 'runtime')" //need to declare 'runtime' as it is not teh default here anymore

        then:
        artifacts() == ['/java-lib/build/libs/java-lib.jar']
        executed ":java-lib:classes"
        executed ':java-lib:jar'
    }

    def "resolves classes.jar directly from local android library via runtime configuration"() {
        when:
        dependency "project(path: ':android-lib', configuration: 'runtime')"

        then:
        artifacts() == ['/android-lib/aar-image/classes.jar']
        executed ":android-lib:classes"
        executed ":android-lib:jar"
        notExecuted ":android-lib:aar"
    }
}
