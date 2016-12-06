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

class DexDownloadedAarTransformIntegrationTest extends AbstractAndroidFilterAndTransformIntegrationTest {

    def "One predex file is produce for a downloaded AAR file"() {
        when:
        dependency "'org.gradle:ext-android-lib-with-jars:1.0'"

        then:
        dex() == [
            '/android-app/transformed/pre-dexed/ext-android-lib-with-jars-1.0.aar_classes.jar_850551400_noJumbo_862294780.predex',
            '/android-app/transformed/pre-dexed/main_noJumbo_1503502270.predex'
        ]
        file('android-app/transformed/pre-dexed/ext-android-lib-with-jars-1.0.aar_classes.jar_850551400_noJumbo_862294780.predex').text ==
            'Predexed from: [/android-app/transformed/expandedArchives/ext-android-lib-with-jars-1.0.aar_classes.jar_850551400, ' +
            '/android-app/transformed/expandedArchives/ext-android-lib-with-jars-1.0.aar_dep1.jar_850551400, ' +
            '/android-app/transformed/expandedArchives/ext-android-lib-with-jars-1.0.aar_dep2.jar_850551400]'
    }

    def "Only one predex file is produced for a downloaded AAR file when it appears in multiple dependencies"() {
        when:
        dependency "'org.gradle:ext-android-lib-with-jars:1.0'"
        dependency "project(':android-lib')"
        libDependency("android-lib", "'org.gradle:ext-android-lib-with-jars:1.0'")

        then:
        dex() == [
            '/android-app/transformed/pre-dexed/ext-android-lib-with-jars-1.0.aar_classes.jar_850551400_noJumbo_862294780.predex',
            '/android-app/transformed/pre-dexed/main_noJumbo_508835034.predex',
            '/android-app/transformed/pre-dexed/main_noJumbo_1503502270.predex'
        ]
        testDirectory.allDescendants().count { it.endsWith(".predex") } == 3
        preDexExecutions() == 3
    }

    def "Running predex twice for a downloaded AAR file with different options produces different outputs"() {
        when:
        dependency "'org.gradle:ext-android-lib-with-jars:1.0'"

        then:
        dex() == [
            '/android-app/transformed/pre-dexed/ext-android-lib-with-jars-1.0.aar_classes.jar_850551400_noJumbo_862294780.predex',
            '/android-app/transformed/pre-dexed/main_noJumbo_1503502270.predex'
        ]
        dex(true, true) == [
            '/android-app/transformed/pre-dexed/ext-android-lib-with-jars-1.0.aar_classes.jar_850551400_jumbo_862294780.predex',
            '/android-app/transformed/pre-dexed/main_jumbo_1503502270.predex'
        ]
        testDirectory.allDescendants().count { it.endsWith(".predex") } == 4
    }

    def "Predex is skipping the actual work for a downloaded AAR file when it is turned off"() {
        when:
        dependency "'org.gradle:ext-android-lib-with-jars:1.0'"

        then:
        dex(false) == [
            '/android-app/transformed/expandedArchives/ext-android-lib-with-jars-1.0.aar_classes.jar_850551400',
            '/android-app/transformed/expandedArchives/ext-android-lib-with-jars-1.0.aar_dep1.jar_850551400',
            '/android-app/transformed/expandedArchives/ext-android-lib-with-jars-1.0.aar_dep2.jar_850551400',
            '/android-app/build/classes/main'
        ]
        testDirectory.allDescendants().count { it.endsWith(".predex") } == 0
        preDexExecutions() == 2
    }


}
