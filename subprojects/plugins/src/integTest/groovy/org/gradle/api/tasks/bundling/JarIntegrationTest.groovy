/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.tasks.bundling

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.archives.TestReproducibleArchives
import org.gradle.internal.reflect.problems.ValidationProblemId
import org.gradle.internal.reflect.validation.ValidationMessageChecker
import org.gradle.internal.reflect.validation.ValidationTestFor
import org.gradle.test.fixtures.archive.JarTestFixture
import org.gradle.util.internal.TextUtil

@TestReproducibleArchives
class JarIntegrationTest extends AbstractIntegrationSpec implements ValidationMessageChecker {
    def setup() {
        expectReindentedValidationMessage()
    }

    def canCreateAnEmptyJar() {
        given:
        buildFile << """
        task jar(type: Jar) {
            from 'test'
            destinationDirectory = buildDir
            archiveFileName = 'test.jar'
        }
        """

        when:
        run 'jar'

        then:
        def jar = new JarTestFixture(file('build/test.jar'))
        jar.assertFileContent('META-INF/MANIFEST.MF', 'Manifest-Version: 1.0\r\n\r\n')
    }

    def canCreateAJarArchiveWithDefaultManifest() {
        given:
        createDir('test') {
            dir1 {
                file 'file1.txt'
            }
        }
        createDir('meta-inf') {
            file 'file1.txt'
            dir2 {
                file 'file2.txt'
            }
        }
        and:
        buildFile << """
            task jar(type: Jar) {
                from 'test'
                metaInf {
                    from 'meta-inf'
                }
                destinationDirectory = buildDir
                archiveFileName = 'test.jar'
            }
        """

        when:
        run 'jar'

        then:
        def jar = new JarTestFixture(file('build/test.jar'))
        jar.assertContainsFile('META-INF/file1.txt')
        jar.assertContainsFile('META-INF/dir2/file2.txt')
        jar.assertContainsFile('dir1/file1.txt')
    }

    def "manifest is the first file in the Jar"() {
        given:
        createDir('meta-inf') {
            file('AAA.META') << 'Some custom metadata'
        }
        buildFile << """
            task jar(type: Jar) {
                metaInf {
                    from 'meta-inf'
                }
                destinationDirectory = buildDir
                archiveFileName = 'test.jar'
            }
        """

        when:
        succeeds 'jar'

        then:
        def jar = new JarTestFixture(file('build/test.jar'))
        jar.assertContainsFile('META-INF/AAA.META')
    }

    def metaInfSpecsAreIndependentOfOtherSpec() {
        given:
        createDir('test') {
            dir1 {
                file 'ignored.xml'
                file 'file1.txt'
            }
        }
        createDir('meta-inf') {
            dir2 {
                file 'ignored.txt'
                file 'file2.xml'
            }
        }
        createDir('meta-inf2') {
            file 'file2.txt'
            file 'file2.xml'
        }
        and:
        buildFile << """
            task jar(type: Jar) {
                from 'test'
                include '**/*.txt'
                metaInf {
                    from 'meta-inf'
                    include '**/*.xml'
                }
                metaInf {
                    from 'meta-inf2'
                    into 'dir3'
                }
                destinationDirectory = buildDir
                archiveFileName = 'test.jar'
            }
        """

        when:
        run 'jar'

        then:
        def jar = new JarTestFixture(file('build/test.jar'))
        jar.assertContainsFile('META-INF/MANIFEST.MF')
        jar.assertContainsFile('META-INF/dir2/file2.xml')
        jar.assertContainsFile('META-INF/dir3/file2.txt')
        jar.assertContainsFile('META-INF/dir3/file2.xml')
        jar.assertContainsFile('dir1/file1.txt')
    }

    def usesManifestFromJarTaskWhenMergingJars() {
        given:
        createDir('src1') {
            dir1 { file 'file1.txt' }
        }
        createDir('src2') {
            dir2 { file 'file2.txt' }
        }
        buildFile << '''
            task jar1(type: Jar) {
                from 'src1'
                destinationDirectory = buildDir
                archiveFileName = 'test1.zip'
                manifest { attributes(attr: 'jar1') }
            }
            task jar2(type: Jar) {
                from 'src2'
                destinationDirectory = buildDir
                archiveFileName = 'test2.zip'
                manifest { attributes(attr: 'jar2') }
            }
            task jar(type: Jar) {
                dependsOn jar1, jar2
                from zipTree(jar1.archiveFile), zipTree(jar2.archiveFile)
                manifest { attributes(attr: 'value') }
                destinationDirectory = buildDir
                archiveFileName = 'test.jar'
            }
            '''

        when:
        run 'jar'

        then:
        def jar = file('build/test.jar')
        def manifest = jar.manifest
        manifest.mainAttributes.getValue('attr') == 'value'

        def jarFixture = new JarTestFixture(jar)
        jarFixture.assertContainsFile('META-INF/MANIFEST.MF')
        jarFixture.assertContainsFile('dir2/file2.txt')
        jarFixture.assertContainsFile('dir2/file2.txt')
    }

    def excludeDuplicatesUseManifestOverMetaInf() {
        createDir('meta-inf') {
            file 'MANIFEST.MF'
        }
        buildFile << '''
        task jar(type: Jar) {
            duplicatesStrategy = 'exclude'
            metaInf {
                from 'meta-inf'
            }
            manifest {
                attributes(attr: 'from manifest')
            }
            destinationDirectory = buildDir
            archiveFileName = 'test.jar'
        }

        '''

        when:
        run 'jar'

        then:
        def jar = file('build/test.jar')
        def manifest = jar.manifest
        manifest.mainAttributes.getValue('attr') == 'from manifest'
    }

    def excludeDuplicatesUseMetaInfOverRegularFiles() {
        createDir('meta-inf1') {
            file 'file.txt'
        }

        createDir('meta-inf2') {
            file 'file.txt'
        }

        file('meta-inf1/file.txt').text = 'good'
        file('meta-inf2/file.txt').text = 'bad'


        buildFile << '''
        task jar(type: Jar) {
            duplicatesStrategy = 'exclude'
            // this should be excluded even though it comes first
            into('META-INF') {
                from 'meta-inf2'
            }
            metaInf {
                from 'meta-inf1'
            }
            destinationDirectory = buildDir
            archiveFileName = 'test.jar'
        }

        '''

        when:
        run 'jar'

        then:
        def jar = new JarTestFixture(file('build/test.jar'))
        jar.assertFileContent('META-INF/file.txt', 'good')
    }

    def duplicateServicesIncludedOthersExcluded() {
        createParallelDirsWithServices()

        given:
        buildFile << '''
        task jar(type: Jar) {
            archiveFileName = 'test.jar'
            destinationDirectory = projectDir
            from 'dir1'
            from 'dir2'
            eachFile {
                it.duplicatesStrategy = it.relativePath.toString().startsWith('META-INF/services/') ? 'include' : 'exclude'
            }
        }
        '''

        when:
        run 'jar'

        then:
        confirmDuplicateServicesPreserved()
    }

    def duplicatesExcludedByDefaultWithExceptionForServices() {
        createParallelDirsWithServices()

        given:
        buildFile << '''
        task jar(type: Jar) {
            archiveFileName = 'test.jar'
            destinationDirectory = projectDir
            from 'dir1'
            from 'dir2'
            duplicatesStrategy = 'exclude'
            filesMatching ('META-INF/services/**') {
                duplicatesStrategy = 'include'
            }
        }
        '''

        when:
        run 'jar'

        then:
        confirmDuplicateServicesPreserved()
    }

    def "changes to manifest attributes should be honoured by incremental build"() {
        given:
        def jarWithManifest = { manifest ->
            """
            task jar(type: Jar) {
                from 'test'
                destinationDirectory = buildDir
                archiveFileName = 'test.jar'
                manifest { $manifest }
            }"""
        }

        createDir('test') {
            dir1 {
                file 'file1.txt'
            }
        }
        def jar = file('build/test.jar')

        when:
        buildFile.text = jarWithManifest("")
        run 'jar'

        then:
        jar.manifest.mainAttributes.getValue('attr') == null

        when: "Attribute added"
        buildFile.text = jarWithManifest("attributes(attr: 'Hello')")
        run 'jar'

        then:
        executedAndNotSkipped ':jar'
        jar.manifest.mainAttributes.getValue('attr') == 'Hello'

        when: "Attribute modified"
        buildFile.text = jarWithManifest("attributes(attr: 'Hi')")
        run 'jar'

        then:
        executedAndNotSkipped ':jar'
        jar.manifest.mainAttributes.getValue('attr') == 'Hi'

        when: "Attribute removed"
        buildFile.text = jarWithManifest("")
        run 'jar'

        then:
        executedAndNotSkipped ':jar'
        jar.manifest.mainAttributes.getValue('attr') == null
    }

    private def createParallelDirsWithServices() {
        createDir('dir1') {
            'META-INF' {
                services {
                    file('org.gradle.Service')
                }
            }
            path {
                file 'test.txt'
            }
        }
        createDir('dir2') {
            'META-INF' {
                services {
                    file('org.gradle.Service')
                }
            }
            file {
                file 'test.txt'
            }
        }

        file('dir1/META-INF/services/org.gradle.Service').write('org.gradle.DefaultServiceImpl')
        file('dir2/META-INF/services/org.gradle.Service').write('org.gradle.BetterServiceImpl')
        file('dir1/test.txt').write('Content of first file')
        file('dir2/test.txt').write('Content of second file')
    }

    private def confirmDuplicateServicesPreserved() {
        def jar = new JarTestFixture(file('test.jar'))

        assert 2 == jar.countFiles('META-INF/services/org.gradle.Service')
        assert 1 == jar.countFiles('path/test.txt')

        jar.assertFileContent('test.txt', 'Content of first file')
        jar.hasService('org.gradle.Service', 'org.gradle.BetterServiceImpl')
        jar.hasService('org.gradle.Service', 'org.gradle.DefaultServiceImpl')
    }

    def "JAR task is skipped when compiler output is unchanged"() {
        file("src/main/java/Main.java") << "public class Main {}\n"
        buildFile << """
            apply plugin: "java"
        """

        succeeds "jar"

        file("src/main/java/Main.java") << "// This should not influence compiled output"

        when:
        succeeds "jar"
        then:
        executedAndNotSkipped ":compileJava"
        skipped ":jar"
    }

    @ValidationTestFor(
        ValidationProblemId.VALUE_NOT_SET
    )
    def "cannot create a JAR without destination dir"() {
        given:
        buildFile << """
            task jar(type: Jar) {
                archiveFileName = 'some.jar'
            }
        """

        when:
        fails('jar')

        then:
        failureDescriptionContains(missingValueMessage { type('org.gradle.api.tasks.bundling.Jar').property('archiveFile') })
    }

    def "can use Provider values in manifest attribute"() {
        given:
        buildFile << """
            task jar(type: Jar) {
                manifest {
                    attributes(attr: provider { "value" })
                    attributes(version: archiveVersion)
                }
                destinationDirectory = buildDir
                archiveFileName = 'test.jar'
                archiveVersion = "1.0"
            }
        """

        when:
        succeeds 'jar'

        then:
        def jar = new JarTestFixture(file('build/test.jar'))
        jar.manifest.mainAttributes.getValue('attr') == 'value'
        jar.manifest.mainAttributes.getValue('version') == '1.0'
    }

    def "can use Provider values in manifest attribute when merging with manifest file"() {
        given:
        def manifest = file("MANIFEST.MF") << "$manifestContent"
        buildFile << """
            task jar(type: Jar) {
                manifest {
                    from("${TextUtil.normaliseFileSeparators(manifest.absolutePath)}")
                    attributes(attr: provider { "value" })
                    attributes(version: archiveVersion)
                }
                destinationDirectory = buildDir
                archiveFileName = 'test.jar'
                archiveVersion = "1.0"
            }
        """

        when:
        succeeds 'jar'

        then:
        def jar = new JarTestFixture(file('build/test.jar'))
        jar.manifest.mainAttributes.getValue('attr') == 'value'
        jar.manifest.mainAttributes.getValue('version') == "$expectedVersion"

        where:
        manifestContent << ["", "version: 0.0.1"]
        expectedVersion << ["1.0", "0.0.1"]
    }

    def "attribute value evaluates lazily"() {
        given:
        buildFile << """
            def versionNumber = objects.property(String)
            versionNumber.set("1.0")

            task jar(type: Jar) {
                manifest {
                    attributes(attr: provider { "value" })
                    attributes(version: archiveVersion)
                }
                destinationDirectory = buildDir
                archiveFileName = 'test.jar'
                archiveVersion = versionNumber
            }

            afterEvaluate {
                versionNumber.set("2.0")
            }
        """

        when:
        succeeds 'jar'

        then:
        def jar = new JarTestFixture(file('build/test.jar'))
        jar.manifest.mainAttributes.getValue('attr') == 'value'
        jar.manifest.mainAttributes.getValue('version') == "2.0"
    }

    def "attribute value evaluates lazily when merging with manifest file"() {
        given:
        def manifest = file("MANIFEST.MF") << ""
        buildFile << """
            def versionNumber = objects.property(String)
            versionNumber.set("1.0")

            task jar(type: Jar) {
                manifest {
                    from("${TextUtil.normaliseFileSeparators(manifest.absolutePath)}")
                    attributes(attr: provider { "value" })
                    attributes(version: archiveVersion)
                }
                destinationDirectory = buildDir
                archiveFileName = 'test.jar'
                archiveVersion = versionNumber
            }

            afterEvaluate {
                versionNumber.set("2.0")
            }
        """

        when:
        succeeds 'jar'

        then:
        def jar = new JarTestFixture(file('build/test.jar'))
        jar.manifest.mainAttributes.getValue('attr') == 'value'
        jar.manifest.mainAttributes.getValue('version') == "2.0"
    }
}
