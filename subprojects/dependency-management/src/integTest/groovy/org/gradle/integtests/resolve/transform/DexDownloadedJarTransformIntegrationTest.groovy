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

class DexDownloadedJarTransformIntegrationTest extends AbstractAndroidFilterAndTransformIntegrationTest {

    def "One predex file is produce for a downloaded JAR file"() {
        when:
        dependency "'org.gradle:ext-java-lib:1.0'"

        then:
        dex() == [
            '/android-app/transformed/pre-dexed/android-app_maven-repo_ext-java-lib-1.0.jar_noJumbo.predex',
            '/android-app/transformed/pre-dexed/android-app_main_noJumbo.predex'
        ]
        TextUtil.normaliseFileSeparators(file('/android-app/transformed/pre-dexed/android-app_maven-repo_ext-java-lib-1.0.jar_noJumbo.predex').text) ==
            'Predexed from: [/android-app/transformed/expandedArchives/maven-repo_ext-java-lib-1.0.jar]'
    }

    def "Only one predex file is produced for a downloaded JAR file when it appears in multiple dependencies"() {
        when:
        dependency "'org.gradle:ext-java-lib:1.0'"
        dependency "project(':android-lib')"
        libDependency("android-lib", "'org.gradle:ext-java-lib:1.0'")

        then:
        dex() == [
            '/android-app/transformed/pre-dexed/android-app_maven-repo_ext-java-lib-1.0.jar_noJumbo.predex',
            '/android-app/transformed/pre-dexed/android-lib_main_noJumbo.predex',
            '/android-app/transformed/pre-dexed/android-app_main_noJumbo.predex'
        ]
        testDirectory.allDescendants().count { it.endsWith(".predex") } == 3
        preDexExecutions() == 3
    }

    def "Running predex twice for a downloaded JAR file with different options produces different outputs"() {
        when:
        dependency "'org.gradle:ext-java-lib:1.0'"

        then:
        dex() == [
            '/android-app/transformed/pre-dexed/android-app_maven-repo_ext-java-lib-1.0.jar_noJumbo.predex',
            '/android-app/transformed/pre-dexed/android-app_main_noJumbo.predex'
        ]
        dex(true, true) == [
            '/android-app/transformed/pre-dexed/android-app_maven-repo_ext-java-lib-1.0.jar_jumbo.predex',
            '/android-app/transformed/pre-dexed/android-app_main_jumbo.predex'
        ]
        testDirectory.allDescendants().count { it.endsWith(".predex") } == 4
    }

    def "Predex is skipped for a downloaded JAR file when it is turned off"() {
        when:
        dependency "'org.gradle:ext-java-lib:1.0'"

        then:
        dex(false) == [
            '/android-app/transformed/expandedArchives/maven-repo_ext-java-lib-1.0.jar',
            '/android-app/build/classes/main'
        ]
        testDirectory.allDescendants().count { it.endsWith(".predex") } == 0
        preDexExecutions() == 2
    }


}
