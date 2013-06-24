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
import org.gradle.test.fixtures.archive.JarTestFixture

import static org.hamcrest.Matchers.*

class JarIntegrationTest extends AbstractIntegrationSpec {

    def canCreateAnEmptyJar() {
        given:
        buildFile << """
                task jar(type: Jar) {
                    from 'test'
                    destinationDir = buildDir
                    archiveName = 'test.jar'
        }
        """
        when:
        run 'jar'
        then:
        def expandDir = file('expanded')
        file('build/test.jar').unzipTo(expandDir)
        expandDir.assertHasDescendants('META-INF/MANIFEST.MF')
        expandDir.file('META-INF/MANIFEST.MF').assertContents(equalTo('Manifest-Version: 1.0\r\n\r\n'))
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
                destinationDir = buildDir
                archiveName = 'test.jar'
            }
        """
        when:
        run 'jar'
        then:
        def expandDir = file('expanded')
        file('build/test.jar').unzipTo(expandDir)
        expandDir.assertHasDescendants('META-INF/MANIFEST.MF', 'META-INF/file1.txt', 'META-INF/dir2/file2.txt', 'dir1/file1.txt')
        expandDir.file('META-INF/MANIFEST.MF').assertContents(equalTo('Manifest-Version: 1.0\r\n\r\n'))
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
                destinationDir = buildDir
                archiveName = 'test.jar'
            }
        """
        when:
        run 'jar'
        then:
        def expandDir = file('expanded')
        file('build/test.jar').unzipTo(expandDir)
        expandDir.assertHasDescendants(
                'META-INF/MANIFEST.MF',
                'META-INF/dir2/file2.xml',
                'META-INF/dir3/file2.txt',
                'META-INF/dir3/file2.xml',
                'dir1/file1.txt')
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
                destinationDir = buildDir
                archiveName = 'test1.zip'
                manifest { attributes(attr: 'jar1') }
            }
            task jar2(type: Jar) {
                from 'src2'
                destinationDir = buildDir
                archiveName = 'test2.zip'
                manifest { attributes(attr: 'jar2') }
            }
            task jar(type: Jar) {
                dependsOn jar1, jar2
                from zipTree(jar1.archivePath), zipTree(jar2.archivePath)
                manifest { attributes(attr: 'value') }
                destinationDir = buildDir
                archiveName = 'test.zip'
            }
            '''
        when:
        run 'jar'
        then:
        def jar = file('build/test.zip')
        def manifest = jar.manifest
        manifest.mainAttributes.getValue('attr') == 'value'

        def expandDir = file('expected')
        jar.unzipTo(expandDir)
        expandDir.assertHasDescendants('dir1/file1.txt', 'dir2/file2.txt', 'META-INF/MANIFEST.MF')
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
            destinationDir = buildDir
            archiveName = 'test.jar'
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
            destinationDir = buildDir
            archiveName = 'test.jar'
        }

        '''
        when:
        run 'jar'
        then:
        def jar = file('build/test.jar')
        jar.unzipTo(file('expected'))
        def target = file('expected/META-INF/file.txt')

        then:
        target.assertIsFile()
        target.text == 'good'
    }

    def duplicateServicesIncludedOthersExcluded() {
        createParallelDirsWithServices()

        given:
        buildFile << '''
        task jar(type: Jar) {
            archiveName = 'test.jar'
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
            archiveName = 'test.jar'
            from 'dir1'
            from 'dir2'
            duplicatesStrategy = 'exclude'
            matching ('META-INF/services/**') {
                duplicatesStrategy = 'include'
            }
        }

        '''
        when:
        run 'jar'
        then:
        confirmDuplicateServicesPreserved()
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

        2 == jar.countFiles('META-INF/services/org.gradle.Service')
        1 == jar.countFiles('path/test.txt')

        jar.assertTextFileContent(hasItem('Content of first file'))
        jar.assertTextFileContent(not(hasItem('Content of second file')))
        jar.hasService('org.gradle.BetterServiceImpl')
        jar.hasService('org.gradle.DefaultServiceImpl')
    }

}
