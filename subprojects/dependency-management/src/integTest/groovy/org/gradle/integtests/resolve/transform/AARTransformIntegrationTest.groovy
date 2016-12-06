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

class AARTransformIntegrationTest extends AbstractAndroidFilterAndTransformIntegrationTest {

    def "classpath artifacts include jars from published java module"() {
        when:
        dependency "'org.gradle:ext-java-lib:1.0'"

        then:
        artifacts('classpath') == ['/maven-repo/org/gradle/ext-java-lib/1.0/ext-java-lib-1.0.jar']
    }

    def "classpath artifacts include jars from file dependencies"() {
        given:
        buildFile << """
            file('android-app/a').mkdir()
            def b = file('android-app/a/b.class')
            b.text = 'b'
            ant.zip(destfile: 'android-app/a.jar') {
                fileset(dir: 'android-app/a')
            }
        """

        when:
        dependency "files('a.jar')"

        then:
        artifacts('classpath') == ['/android-app/a.jar']
    }

    def "classpath artifacts include classes.jar and libs extracted from published android module"() {
        when:
        dependency "'org.gradle:ext-android-lib-with-jars:1.0'"

        then:
        artifacts('classpath') == [
            '/android-app/transformed/ext-android-lib-with-jars-1.0.aar/explodedAar/classes.jar',
            '/android-app/transformed/ext-android-lib-with-jars-1.0.aar/explodedAar/libs/dep1.jar',
            '/android-app/transformed/ext-android-lib-with-jars-1.0.aar/explodedAar/libs/dep2.jar'
        ]
    }

    def "classpath artifacts include class folders from local libraries and jars from published java module"() {
        when:
        dependency "project(':java-lib')"
        dependency "project(':android-lib')"
        dependency "'org.gradle:ext-java-lib:1.0'"
        dependency "'org.gradle:ext-android-lib-with-jars:1.0'"

        then:
        artifacts('classpath') == [
            '/java-lib/build/classes/main',
            '/android-lib/build/classes/main',
            '/maven-repo/org/gradle/ext-java-lib/1.0/ext-java-lib-1.0.jar',
            '/android-app/transformed/ext-android-lib-with-jars-1.0.aar/explodedAar/classes.jar',
            '/android-app/transformed/ext-android-lib-with-jars-1.0.aar/explodedAar/libs/dep1.jar',
            '/android-app/transformed/ext-android-lib-with-jars-1.0.aar/explodedAar/libs/dep2.jar'
        ]
    }

    // processClasses filtering and transformation

    def "classes artifacts include class folder from published java modules"() {
        when:
        dependency "'org.gradle:ext-java-lib:1.0'"

        then:
        artifacts('classes') == ['/android-app/transformed/expandedArchives/maven-repo_ext-java-lib-1.0.jar']
    }

    def "classes artifacts include class folder extracted from published android modules"() {
        when:
        dependency "'org.gradle:ext-android-lib:1.0'"

        then:
        artifacts('classes') == ['/android-app/transformed/expandedArchives/maven-repo_ext-android-lib-1.0.aar_classes.jar']
    }

    def "classes artifacts include class folders extracted from jar file dependencies"() {
        given:
        buildFile << """
            file('android-app/a').mkdir()
            def b = file('android-app/a/b.class')
            b.text = 'b'
            ant.zip(destfile: 'android-app/a.jar') {
                fileset(dir: 'android-app/a')
            }
        """

        when:
        dependency "files('a.jar')"

        then:
        artifacts('classes') == ['/android-app/transformed/expandedArchives/android-app_a.jar']
    }

    def "classes artifacts include class folders from projects and libraries"() {
        when:
        dependency "project(':java-lib')"
        dependency "project(':android-lib')"
        dependency "'org.gradle:ext-java-lib:1.0'"
        dependency "'org.gradle:ext-android-lib:1.0'"

        then:
        artifacts('classes') == [
            '/java-lib/build/classes/main',
            '/android-lib/build/classes/main',
            '/android-app/transformed/expandedArchives/maven-repo_ext-java-lib-1.0.jar',
            '/android-app/transformed/expandedArchives/maven-repo_ext-android-lib-1.0.aar_classes.jar'
        ]
    }

    // processManifests filtering and transformation

    def "no manifest for local java library or published java module"() {
        when:
        dependency "project(':java-lib')"
        dependency "'org.gradle:ext-java-lib:1.0'"

        then:
        artifacts('android-manifest') == []

        and:
        executedTasks == [":android-app:printArtifacts"]
    }

    def "manifest returned for local android library"() {
        when:
        dependency "project(':android-lib')"

        then:
        artifacts('android-manifest') == ['/android-lib/aar-image/AndroidManifest.xml']

        and:
        executedTasks == [":android-app:printArtifacts"]
    }

    def "manifest returned for published android module"() {
        when:
        dependency "'org.gradle:ext-android-lib:1.0'"

        then:
        artifacts('android-manifest') == ['/android-app/transformed/ext-android-lib-1.0.aar/explodedAar/AndroidManifest.xml']
    }

    def "manifests returned for a combination of java and android libraries"() {
        when:
        dependency "project(':java-lib')"
        dependency "project(':android-lib')"
        dependency "'org.gradle:ext-java-lib:1.0'"
        dependency "'org.gradle:ext-android-lib:1.0'"

        then:
        artifacts('android-manifest') == [
            '/android-lib/aar-image/AndroidManifest.xml',
            '/android-app/transformed/ext-android-lib-1.0.aar/explodedAar/AndroidManifest.xml'
        ]
    }
}
