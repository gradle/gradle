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

package org.gradle.plugin.devel.impldeps

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class PackageListGeneratorIntegrationTest extends AbstractIntegrationSpec {

    private static final ArrayList<String> EXPECTED_PACKAGE_LIST = ['com/acme', 'com/foo/internal', 'javax/servlet/http']
    private static final DEFAULT_EXCLUDES_FOR_TEST = [
        "org/gradle",
        "java",
        "javax/xml",
        "javax/inject",
        "groovy",
        "groovyjarjarantlr",
        "net/rubygrapefruit",
        "org/codehaus/groovy",
        "org/apache/tools/ant",
        "org/apache/commons/logging",
        "org/slf4j",
        "org/apache/log4j",
        "org/apache/xerces",
        "org/w3c/dom",
        "org/xml/sax",
        "sun/misc"
    ]

    def "generates a curated list of package prefixes from directories"() {
        given:
        buildFile << """
            task generatePackageList(type: org.gradle.api.internal.runtimeshaded.PackageListGenerator) {
                excludes = ['${DEFAULT_EXCLUDES_FOR_TEST.join("', '")}']
                outputFile = file("\$buildDir/package-list.txt")
                classpath = files("\$buildDir/classes")
            }
        """
        and:
        someClasses()

        when:
        succeeds 'generatePackageList'

        then:
        def packagesFile = file('build/package-list.txt')
        packagesFile.exists()
        Set<String> packages = packagesFile as String[]
        packages == EXPECTED_PACKAGE_LIST as Set
    }

    def "generates a curated list of package prefixes from jars"() {
        given:
        someClasses()
        aJar()

        and:
        buildFile << """
            task generatePackageList(type: org.gradle.api.internal.runtimeshaded.PackageListGenerator) {
                dependsOn jar
                excludes = ['${DEFAULT_EXCLUDES_FOR_TEST.join("', '")}']
                outputFile = file("\$buildDir/package-list.txt")
                classpath = files(jar.archivePath)
            }
        """

        when:
        succeeds 'generatePackageList'

        then:
        def packagesFile = file('build/package-list.txt')
        packagesFile.exists()
        Set<String> packages = packagesFile as String[]
        packages == EXPECTED_PACKAGE_LIST as Set
    }

    def "package list excludes default packages"() {
        given:
        buildFile << """
            task generatePackageList(type: org.gradle.api.internal.runtimeshaded.PackageListGenerator) {
                excludes = ['${DEFAULT_EXCLUDES_FOR_TEST.join("', '")}']
                outputFile = file("\$buildDir/package-list.txt")
                classpath = files("\$buildDir/classes")
            }
        """
        and:
        someClasses()
        someClassesInDefaultPackages()

        when:
        succeeds 'generatePackageList'

        then:
        def packagesFile = file('build/package-list.txt')
        packagesFile.exists()
        Set<String> packages = packagesFile as String[]
        packages == EXPECTED_PACKAGE_LIST as Set
    }

    private void someClasses() {
        file('build/classes/com/acme/Foo.class') << ''
        file('build/classes/com/acme/internal/FooInternal.class') << ''
        file('build/classes/com/foo/internal/FooInternal.class') << ''
        file('build/classes/javax/servlet/http/HttpServletRequest.class') << ''
    }

    private void someClassesInDefaultPackages() {
        DEFAULT_EXCLUDES_FOR_TEST.eachWithIndex { pkg, i ->
            file("build/classes/$pkg/Foo${i}.class") << ''
        }
    }

    private void aJar() {
        buildFile << '''
            task jar(type: Jar) {
                archiveFileName = 'mylib.jar'
                destinationDirectory = buildDir
                from file('build/classes')
            }
        '''
    }
}
