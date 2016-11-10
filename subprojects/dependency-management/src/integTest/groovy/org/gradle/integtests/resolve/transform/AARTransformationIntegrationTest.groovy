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

public class AARTransformationIntegrationTest extends AbstractAARTransformIntegrationTest {

    // compileClassesAndResources (unfiltered, no transformations)

    def "compileClassesAndResources references class folder from local java library"() {
        when:
        dependency "project(':java-lib')"

        then:
        artifacts('compileClassesAndResources') == ['/java-lib/build/classes/main']
        executed ":java-lib:classes"
        notExecuted ':java-lib:jar'
    }

    def "compileClassesAndResources references classes folder and manifest from local android library"() {
        when:
        dependency "project(':android-lib')"

        then:
        artifacts('compileClassesAndResources') == ['/android-lib/build/classes/main', '/android-lib/aar-image/AndroidManifest.xml']
        executed ":android-lib:classes"
        notExecuted ":android-lib:jar"
        notExecuted ":android-lib:aar"
    }

    // Working with jars, using 'runtime' instead of 'compileClassesAndResources'

    def "compile references jar from local java library"() {
        when:
        dependency "project(':java-lib')"
        dependency "runtime", "project(path: ':java-lib', configuration: 'runtime')" //need to declare 'runtime' as it is not teh default here anymore

        then:
        artifacts('runtime') == ['/java-lib/build/libs/java-lib.jar']
        executed ":java-lib:classes"
        executed ':java-lib:jar'
    }

    def "compile references classes.jar from local android library"() {
        when:
        dependency "project(':java-lib')"
        dependency "runtime", "project(path: ':android-lib', configuration: 'runtime')"

        then:
        artifacts('runtime') == ['/android-lib/aar-image/classes.jar']
        executed ":android-lib:classes"
        executed ":android-lib:jar"
        notExecuted ":android-lib:aar"
    }

    // processClasses filtering and transformation

    def "processClasspath includes jars from published java modules"() {
        when:
        dependency "'org.gradle:ext-java-lib:1.0'"

        then:
        artifacts('processClasspath') == ['/maven-repo/org/gradle/ext-java-lib/1.0/ext-java-lib-1.0.jar']
    }

    def "processClasspath includes classes.jar from published android modules"() {
        when:
        dependency "'org.gradle:ext-android-lib:1.0'"

        then:
        artifacts('processClasspath') == ['/android-app/transformed/ext-android-lib-1.0.aar/explodedAar/classes.jar']
    }

    def "processClasspath can include jars from file dependencies"() {
        when:
        dependency "gradleApi()"

        then:
        artifacts('processClasspath').size() > 20
        output.contains('.jar\n')
        !output.contains('.jar/classes')
    }

    def "processClasspath includes a combination of project class folders and library jars"() {
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

    def "processClasses includes classes folder from published java modules"() {
        when:
        dependency "'org.gradle:ext-java-lib:1.0'"

        then:
        artifacts('processClasses') == ['/android-app/transformed/ext-java-lib-1.0.jar/classes']
    }

    def "processClasses includes classes folder from published android modules"() {
        when:
        dependency "'org.gradle:ext-android-lib:1.0'"

        then:
        artifacts('processClasses') == ['/android-app/transformed/ext-android-lib-1.0.aar/explodedClassesJar']
    }

    def "processClasses can include classes folders from file dependencies"() {
        when:
        dependency "gradleApi()"

        then:
        artifacts('processClasses').size() > 20
        !output.contains('.jar\n')
        output.contains('.jar/classes')
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
