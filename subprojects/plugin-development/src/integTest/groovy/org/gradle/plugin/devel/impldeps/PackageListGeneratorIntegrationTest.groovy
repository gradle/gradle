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

import org.gradle.api.internal.runtimeshaded.PackageListGenerator
import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class PackageListGeneratorIntegrationTest extends AbstractIntegrationSpec {
    def "generates a curated list of package prefixes from directories"() {
        given:
        buildFile << '''
            task generatePackageList(type: org.gradle.api.internal.runtimeshaded.PackageListGenerator) {
                outputFile = file("$buildDir/package-list.txt")
                classpath = files("$buildDir/classes")
            }
        '''
        and:
        someClasses()

        when:
        succeeds 'generatePackageList'

        then:
        def packagesFile = file('build/package-list.txt')
        packagesFile.exists()
        Set<String> packages = packagesFile as String[]
        packages == ['com/acme', 'com/foo/internal'] as Set
    }

    def "generates a curated list of package prefixes from jars"() {
        given:
        someClasses()
        aJar()

        and:
        buildFile << '''
            task generatePackageList(type: org.gradle.api.internal.runtimeshaded.PackageListGenerator) {
                dependsOn jar
                outputFile = file("$buildDir/package-list.txt")
                classpath = files(jar.archivePath)
            }
        '''

        when:
        succeeds 'generatePackageList'

        then:
        def packagesFile = file('build/package-list.txt')
        packagesFile.exists()
        Set<String> packages = packagesFile as String[]
        packages == ['com/acme', 'com/foo/internal'] as Set
    }

    def "package list excludes default packages"() {
        given:
        buildFile << '''
            task generatePackageList(type: org.gradle.api.internal.runtimeshaded.PackageListGenerator) {
                outputFile = file("$buildDir/package-list.txt")
                classpath = files("$buildDir/classes")
            }
        '''
        and:
        someClasses()
        someClassesInDefaultPackages()

        when:
        succeeds 'generatePackageList'

        then:
        def packagesFile = file('build/package-list.txt')
        packagesFile.exists()
        Set<String> packages = packagesFile as String[]
        packages == ['com/acme', 'com/foo/internal'] as Set
    }

    private void someClasses() {
        file('build/classes/com/acme/Foo.class') << ''
        file('build/classes/com/acme/internal/FooInternal.class') << ''
        file('build/classes/com/foo/internal/FooInternal.class') << ''
    }

    private void someClassesInDefaultPackages() {
        PackageListGenerator.DEFAULT_EXCLUDES.eachWithIndex { pkg, i ->
            file("build/classes/$pkg/Foo${i}.class") << ''
        }
    }

    private void aJar() {
        buildFile << '''
            task jar(type: Jar) {
                archiveName = 'mylib.jar'
                from file('build/classes')
            }
        '''
    }
}
