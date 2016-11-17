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

public class AARTransformIntegrationTest extends AbstractAARFilterAndTransformIntegrationTest {

    // processClasspath filtering and transformation

    def "processClasspath includes jars from published java module"() {
        when:
        dependency "'org.gradle:ext-java-lib:1.0'"

        then:
        artifacts('processClasspath') == ['/maven-repo/org/gradle/ext-java-lib/1.0/ext-java-lib-1.0.jar']
    }

    def "processClasspath includes jars from file dependencies"() {
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
        artifacts('processClasspath') == ['/android-app/a.jar']
    }

    def "processClasspath includes classes.jar from published android module"() {
        when:
        dependency "'org.gradle:ext-android-lib:1.0'"

        then:
        artifacts('processClasspath') == ['/android-app/transformed/ext-android-lib-1.0.aar/explodedAar/classes.jar']
    }

    def "processClasspath includes class folders from local libraries and jars from published java module"() {
        when:
        dependency "project(':java-lib')"
        dependency "project(':android-lib')"
        dependency "'org.gradle:ext-java-lib:1.0'"
        dependency "'org.gradle:ext-android-lib:1.0'"

        then:
        artifacts('processClasspath') == [
            '/java-lib/build/classes/main',
            '/android-lib/build/classes/main',
            '/maven-repo/org/gradle/ext-java-lib/1.0/ext-java-lib-1.0.jar',
            '/android-app/transformed/ext-android-lib-1.0.aar/explodedAar/classes.jar'
        ]
    }

    // processClasses filtering and transformation

    def "processClasses includes class folder from published java modules"() {
        when:
        dependency "'org.gradle:ext-java-lib:1.0'"

        then:
        artifacts('processClasses') == ['/android-app/transformed/ext-java-lib-1.0.jar/classes']
    }

    def "processClasses includes class folder from published android modules"() {
        when:
        dependency "'org.gradle:ext-android-lib:1.0'"

        then:
        artifacts('processClasses') == ['/android-app/transformed/ext-android-lib-1.0.aar/explodedClassesJar']
    }

    def "processClasses includes class folders from file dependencies"() {
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
        artifacts('processClasses') == ['/android-app/transformed/a.jar/classes']
    }

    def "processClasses includes class folders from projects and libraries"() {
        when:
        dependency "project(':java-lib')"
        dependency "project(':android-lib')"
        dependency "'org.gradle:ext-java-lib:1.0'"
        dependency "'org.gradle:ext-android-lib:1.0'"

        then:
        artifacts('processClasses') == [
            '/java-lib/build/classes/main',
            '/android-lib/build/classes/main',
            '/android-app/transformed/ext-java-lib-1.0.jar/classes',
            '/android-app/transformed/ext-android-lib-1.0.aar/explodedClassesJar'
        ]
    }

    // processManifests filtering and transformation

    def "no manifest for local java library or published java module"() {
        when:
        dependency "project(':java-lib')"
        dependency "'org.gradle:ext-java-lib:1.0'"

        then:
        artifacts('processManifests') == []
    }

    def "manifest returned for local android library"() {
        when:
        dependency "project(':android-lib')"

        then:
        artifacts('processManifests') == ['/android-lib/aar-image/AndroidManifest.xml']
    }

    def "manifest returned for published android module"() {
        when:
        dependency "'org.gradle:ext-android-lib:1.0'"

        then:
        artifacts('processManifests') == ['/android-app/transformed/ext-android-lib-1.0.aar/explodedAar/AndroidManifest.xml']
    }

    def "manifests returned for a combination of aars and jars"() {
        when:
        dependency "project(':java-lib')"
        dependency "project(':android-lib')"
        dependency "'org.gradle:ext-java-lib:1.0'"
        dependency "'org.gradle:ext-android-lib:1.0'"

        then:
        artifacts('processManifests') == [
            '/android-lib/aar-image/AndroidManifest.xml',
            '/android-app/transformed/ext-android-lib-1.0.aar/explodedAar/AndroidManifest.xml'
        ]
    }
}
