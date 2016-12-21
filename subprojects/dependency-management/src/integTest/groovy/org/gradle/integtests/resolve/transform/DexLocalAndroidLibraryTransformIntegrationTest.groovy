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

import org.gradle.util.TextUtil

class DexLocalAndroidLibraryTransformIntegrationTest extends AbstractAndroidFilterAndTransformIntegrationTest {

    def "One predex file is produce for a local Android library"() {
        when:
        dependency "project(':android-lib')"

        then:
        dex() == [
            '/android-app/transformed/pre-dexed/android-lib_main_noJumbo.predex',
            '/android-app/transformed/pre-dexed/android-app_main_noJumbo.predex'
        ]
        TextUtil.normaliseFileSeparators(file('/android-app/transformed/pre-dexed/android-lib_main_noJumbo.predex').text) ==
            'Predexed from: [/android-lib/build/classes/main]'
    }

    def "Only one predex file is produce for a local Android library when it appears in multiple dependencies"() {
        given:
        buildFile << localAndroidLibrary('android-lib-2')

        when:
        dependency "project(':android-lib')"
        dependency "project(':android-lib-2')"
        libDependency("android-lib-2", "project(':android-lib')")

        then:
        dex() == [
            '/android-app/transformed/pre-dexed/android-lib_main_noJumbo.predex',
            '/android-app/transformed/pre-dexed/android-lib-2_main_noJumbo.predex',
            '/android-app/transformed/pre-dexed/android-app_main_noJumbo.predex'
        ]
        testDirectory.allDescendants().count { it.endsWith(".predex") } == 3
        preDexExecutions() == 3
    }

    def "Running predex twice for a local Android library with different options produces different outputs"() {
        when:
        dependency "project(':android-lib')"

        then:
        dex() == [
            '/android-app/transformed/pre-dexed/android-lib_main_noJumbo.predex',
            '/android-app/transformed/pre-dexed/android-app_main_noJumbo.predex'
        ]
        dex(true, true) == [
            '/android-app/transformed/pre-dexed/android-lib_main_jumbo.predex',
            '/android-app/transformed/pre-dexed/android-app_main_jumbo.predex'
        ]
        testDirectory.allDescendants().count { it.endsWith(".predex") } == 4
    }

    def "Predex is skipped for a local Android library when it is turned off"() {
        when:
        dependency "project(':android-lib')"

        then:
        dex(false) == [
            '/android-lib/build/classes/main',
            '/android-app/build/classes/main'
        ]
        testDirectory.allDescendants().count { it.endsWith(".predex") } == 0
        preDexExecutions() == 2
    }

    def "No jar is built for a local Android library if the library itself is only used locally and not released"() {
        when:
        dependency "project(':android-lib')"
        dex()

        then:
        notExecuted ':android-lib:jar'
        file('android-lib').allDescendants().count { it.endsWith(".jar") } == 0
    }
}
