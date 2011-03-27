/*
 * Copyright 2010 the original author or authors.
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


package org.gradle.integtests

import org.gradle.util.TestFile
import org.junit.Test
import static org.junit.Assert.*
import static org.hamcrest.Matchers.*
import org.gradle.integtests.fixtures.internal.AbstractIntegrationTest

public class ArchiveIntegrationTest extends AbstractIntegrationTest {
    @Test public void canCopyFromAZip() {
        createZip('test.zip') {
            subdir1 {
                file 'file1.txt'
            }
            subdir2 {
                file 'file2.txt'
                file 'file2.xml'
            }
        }

        testFile('build.gradle') << '''
            task copy(type: Copy) {
                from zipTree('test.zip')
                exclude '**/*.xml'
                into 'dest'
            }
'''

        inTestDirectory().withTasks('copy').run()

        testFile('dest').assertHasDescendants('subdir1/file1.txt', 'subdir2/file2.txt')
    }

    @Test public void canCopyFromATar() {
        createTar('test.tar') {
            subdir1 {
                file 'file1.txt'
            }
            subdir2 {
                file 'file2.txt'
                file 'file2.xml'
            }
        }

        testFile('build.gradle') << '''
            task copy(type: Copy) {
                from tarTree('test.tar')
                exclude '**/*.xml'
                into 'dest'
            }
'''

        inTestDirectory().withTasks('copy').run()

        testFile('dest').assertHasDescendants('subdir1/file1.txt', 'subdir2/file2.txt')
    }

    @Test public void cannotCreateAnEmptyZip() {
        testFile('build.gradle') << '''
            task zip(type: Zip) {
                from 'test'
                destinationDir = buildDir
                archiveName = 'test.zip'
}
'''

        inTestDirectory().withTasks('zip').run()

        testFile('build/test.zip').assertDoesNotExist()
    }

    @Test public void canCreateAnEmptyJar() {
        testFile('build.gradle') << '''
            task jar(type: Jar) {
                from 'test'
                destinationDir = buildDir
                archiveName = 'test.jar'
}
'''

        inTestDirectory().withTasks('jar').run()

        TestFile expandDir = testFile('expanded')
        testFile('build/test.jar').unzipTo(expandDir)
        expandDir.assertHasDescendants('META-INF/MANIFEST.MF')

        expandDir.file('META-INF/MANIFEST.MF').assertContents(equalTo('Manifest-Version: 1.0\r\n\r\n'))
    }

    @Test public void cannotCreateAnEmptyTar() {
        testFile('build.gradle') << '''
            task tar(type: Tar) {
                from 'test'
                destinationDir = buildDir
                archiveName = 'test.tar'
}
'''

        inTestDirectory().withTasks('tar').run()

        testFile('build/test.tar').assertDoesNotExist()
    }

    @Test public void canCreateAZipArchive() {
        createDir('test') {
            dir1 {
                file('file1.txt').write("abc")
            }
            file 'file1.txt'
            dir2 {
                file 'file2.txt'
                file 'script.sh'
            }
        }

        testFile('build.gradle') << '''
            task zip(type: Zip) {
                into('prefix') {
                    from 'test'
                    include '**/*.txt'
                    rename { "renamed_$it" }
                    filter { "[$it]" }
                }
                into('scripts') {
                    from 'test'
                    include '**/*.sh'
                    dirMode = 0750
                    fileMode = 0754
                }
                destinationDir = buildDir
                archiveName = 'test.zip'
            }
'''

        inTestDirectory().withTasks('zip').run()

        TestFile expandDir = testFile('expanded')
        testFile('build/test.zip').usingNativeTools().unzipTo(expandDir)
        expandDir.assertHasDescendants(
                'prefix/dir1/renamed_file1.txt',
                'prefix/renamed_file1.txt',
                'prefix/dir2/renamed_file2.txt',
                'scripts/dir2/script.sh')

        expandDir.file('prefix/dir1/renamed_file1.txt').assertContents(equalTo('[abc]'))

        expandDir.file('prefix/dir1').assertPermissions(equalTo("rwxr-xr-x"))
        expandDir.file('prefix/dir1/renamed_file1.txt').assertPermissions(equalTo("rw-r--r--"))
        expandDir.file('scripts/dir2').assertPermissions(equalTo("rwxr-x---"))
        expandDir.file('scripts/dir2/script.sh').assertPermissions(equalTo("rwxr-xr--"))
    }

    @Test public void canCreateATarArchive() {
        createDir('test') {
            dir1 {
                file('file1.txt') << 'abc'
            }
            file 'file1.txt'
            dir2 {
                file 'file2.txt'
                file 'script.sh'
            }
        }

        testFile('build.gradle') << '''
            task tar(type: Tar) {
                from('test') {
                    include '**/*.txt'
                    filter { "[$it]" }
                }
                from('test') {
                    include '**/*.sh'
                    into 'scripts'
                    fileMode = 0754
                    dirMode = 0750
                }
                destinationDir = buildDir
                archiveName = 'test.tar'
            }
'''

        inTestDirectory().withTasks('tar').run()

        TestFile expandDir = testFile('expanded')
        testFile('build/test.tar').usingNativeTools().untarTo(expandDir)
        expandDir.assertHasDescendants('dir1/file1.txt', 'file1.txt', 'dir2/file2.txt', 'scripts/dir2/script.sh')

        expandDir.file('dir1/file1.txt').assertContents(equalTo('[abc]'))

        expandDir.file('dir1').assertPermissions(equalTo("rwxr-xr-x"))
        expandDir.file('dir1/file1.txt').assertPermissions(equalTo("rw-r--r--"))
        expandDir.file('scripts/dir2').assertPermissions(equalTo("rwxr-x---"))
        expandDir.file('scripts/dir2/script.sh').assertPermissions(equalTo("rwxr-xr--"))
    }

    @Test public void canCreateATgzArchive() {
        createDir('test') {
            dir1 {
                file 'file1.txt'
            }
            file 'file1.txt'
            dir2 {
                file 'file2.txt'
                file 'ignored.xml'
            }
        }

        testFile('build.gradle') << '''
            task tar(type: Tar) {
                compression = Compression.GZIP
                from 'test'
                include '**/*.txt'
                destinationDir = buildDir
                archiveName = 'test.tgz'
            }
'''

        inTestDirectory().withTasks('tar').run()

        TestFile expandDir = testFile('expanded')
        testFile('build/test.tgz').untarTo(expandDir)
        expandDir.assertHasDescendants('dir1/file1.txt', 'file1.txt', 'dir2/file2.txt')
    }

    @Test public void canCreateATbzArchive() {
        createDir('test') {
            dir1 {
                file 'file1.txt'
            }
            file 'file1.txt'
            dir2 {
                file 'file2.txt'
                file 'ignored.xml'
            }
        }

        testFile('build.gradle') << '''
            task tar(type: Tar) {
                compression = Compression.BZIP2
                from 'test'
                include '**/*.txt'
                destinationDir = buildDir
                archiveName = 'test.tbz2'
            }
'''

        inTestDirectory().withTasks('tar').run()

        TestFile expandDir = testFile('expanded')
        testFile('build/test.tbz2').untarTo(expandDir)
        expandDir.assertHasDescendants('dir1/file1.txt', 'file1.txt', 'dir2/file2.txt')
    }

    @Test public void canCreateAJarArchiveWithDefaultManifest() {
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

        testFile('build.gradle') << '''
            task jar(type: Jar) {
                from 'test'
                metaInf {
                    from 'meta-inf'
                }
                destinationDir = buildDir
                archiveName = 'test.jar'
            }
'''

        inTestDirectory().withTasks('jar').run()

        TestFile expandDir = testFile('expanded')
        testFile('build/test.jar').unzipTo(expandDir)
        expandDir.assertHasDescendants('META-INF/MANIFEST.MF', 'META-INF/file1.txt', 'META-INF/dir2/file2.txt', 'dir1/file1.txt')

        expandDir.file('META-INF/MANIFEST.MF').assertContents(equalTo('Manifest-Version: 1.0\r\n\r\n'))
    }

    @Test public void metaInfSpecsAreIndependentOfOtherSpec() {
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

        testFile('build.gradle') << '''
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
'''

        inTestDirectory().withTasks('jar').run()

        TestFile expandDir = testFile('expanded')
        testFile('build/test.jar').unzipTo(expandDir)
        expandDir.assertHasDescendants(
                'META-INF/MANIFEST.MF',
                'META-INF/dir2/file2.xml',
                'META-INF/dir3/file2.txt',
                'META-INF/dir3/file2.xml',
                'dir1/file1.txt')
    }

    @Test public void canCreateAWarArchiveWithNoWebXml() {
        createDir('content') {
            content1 {
                file 'file1.jsp'
            }
        }
        createDir('web-inf') {
            webinf1 {
                file 'file1.txt'
            }
        }
        createDir('meta-inf') {
            metainf1 {
                file 'file2.txt'
            }
        }
        createDir('classes') {
            org {
                gradle {
                    file 'resource.txt'
                    file 'Person.class'
                }
            }
        }
        createZip("lib.jar") {
            file "Dependency.class"
        }

        testFile('build.gradle') << '''
            task war(type: War) {
                from 'content'
                metaInf {
                    from 'meta-inf'
                }
                webInf {
                    from 'web-inf'
                }
                classpath 'classes'
                classpath 'lib.jar'
                destinationDir = buildDir
                archiveName = 'test.war'
            }
'''

        inTestDirectory().withTasks('war').run()

        TestFile expandDir = testFile('expanded')
        testFile('build/test.war').unzipTo(expandDir)
        expandDir.assertHasDescendants(
                'META-INF/MANIFEST.MF',
                'META-INF/metainf1/file2.txt',
                'content1/file1.jsp',
                'WEB-INF/lib/lib.jar',
                'WEB-INF/classes/org/gradle/resource.txt',
                'WEB-INF/classes/org/gradle/Person.class',
                'WEB-INF/webinf1/file1.txt')

        expandDir.file('META-INF/MANIFEST.MF').assertContents(equalTo('Manifest-Version: 1.0\r\n\r\n'))
    }

    @Test public void canCreateAWarArchiveWithWebXml() {
        testFile('some.xml') << '<web/>'
        createDir('web-inf') {
            webinf1 {
                file 'file1.txt'
            }
        }

        testFile('build.gradle') << '''
            task war(type: War) {
                webInf {
                    from 'web-inf'
                    exclude '**/*.xml'
                }
                webXml = file('some.xml')
                destinationDir = buildDir
                archiveName = 'test.war'
            }
'''

        inTestDirectory().withTasks('war').run()

        TestFile expandDir = testFile('expanded')
        testFile('build/test.war').unzipTo(expandDir)
        expandDir.assertHasDescendants(
                'META-INF/MANIFEST.MF',
                'WEB-INF/web.xml',
                'WEB-INF/webinf1/file1.txt')
    }

    @Test public void canAddFilesToWebInfDir() {
        createDir('web-inf') {
            webinf1 {
                file 'file1.txt'
                file 'ignore.xml'
            }
        }
        createDir('web-inf2') {
            file 'file2.txt'
        }

        testFile('build.gradle') << '''
            task war(type: War) {
                webInf {
                    from 'web-inf'
                    exclude '**/*.xml'
                }
                webInf {
                    from 'web-inf2'
                    into 'dir2'
                    include '**/file2*'
                }
                destinationDir = buildDir
                archiveName = 'test.war'
            }
'''

        inTestDirectory().withTasks('war').run()

        TestFile expandDir = testFile('expanded')
        testFile('build/test.war').unzipTo(expandDir)
        expandDir.assertHasDescendants(
                'META-INF/MANIFEST.MF',
                'WEB-INF/webinf1/file1.txt',
                'WEB-INF/dir2/file2.txt')
    }

    @Test public void canCreateArchivesAndExplodedImageFromSameSpec() {
        createDir('test') {
            dir1 {
                file 'file1.txt'
                file 'ignored.xml'
            }
            dir2 {
                dir3 { file 'file2.txt' }
                file 'ignored.xml'
            }
        }

        testFile('build.gradle') << '''
            def distImage = copySpec {
                include '**/*.txt'
                from('test/dir1') {
                    into 'lib'
                }
                from('test/dir2') {
                    into 'src'
                }
            }
            task copy(type: Copy) {
                into 'build/exploded'
                with distImage
            }
            task zip(type: Zip) {
                destinationDir = file('build')
                archiveName = 'test.zip'
                into 'prefix'
                with distImage
            }
'''

        inTestDirectory().withTasks('copy', 'zip').run()
        testFile('build/exploded').assertHasDescendants(
                'lib/file1.txt', 'src/dir3/file2.txt'
        )
        TestFile expandDir = testFile('expanded')
        testFile('build/test.zip').unzipTo(expandDir)
        expandDir.assertHasDescendants('prefix/lib/file1.txt', 'prefix/src/dir3/file2.txt')
    }

    @Test public void canCreateExplodedImageFromArchiveTask() {
        createDir('test') {
            dir1 {
                file 'file1.txt'
                file 'ignored.xml'
            }
            dir2 {
                dir3 { file 'file2.txt' }
                file 'ignored.xml'
            }
        }

        testFile('build.gradle') << '''
            task zip(type: Zip) {
                destinationDir = file('build')
                archiveName = 'test.zip'
                into 'prefix'
                from 'test'
                include '**/*.txt'
            }
            task explodedZip(type: Copy) {
                into 'build/exploded'
                with zip
            }
            task copyFromRootSpec(type: Copy) {
                into 'build/copy'
                with zip.rootSpec
            }
'''

        inTestDirectory().withTasks('explodedZip', 'copyFromRootSpec').run()
        testFile('build/exploded').assertHasDescendants(
                'prefix/dir1/file1.txt', 'prefix/dir2/dir3/file2.txt'
        )
        testFile('build/copy').assertHasDescendants(
                'prefix/dir1/file1.txt', 'prefix/dir2/dir3/file2.txt'
        )
    }

    @Test public void canMergeArchivesIntoAnotherZip() {
        createZip('test.zip') {
            shared {
                file 'zip.txt'
            }
            zipdir1 {
                file 'file1.txt'
            }
        }
        createTar('test.tar') {
            shared {
                file 'tar.txt'
            }
            tardir1 {
                file 'file1.txt'
            }
        }
        createDir('test') {
            shared {
                file 'dir.txt'
            }
            dir1 {
                file 'file1.txt'
            }
        }

        testFile('build.gradle') << '''
        task zip(type: Zip) {
            from zipTree('test.zip')
            from tarTree('test.tar')
            from fileTree('test')
            destinationDir = buildDir
            archiveName = 'test.zip'
        }
        '''

        inTestDirectory().withTasks('zip').run()

        TestFile expandDir = testFile('expanded')
        testFile('build/test.zip').unzipTo(expandDir)
        expandDir.assertHasDescendants('shared/zip.txt', 'zipdir1/file1.txt', 'shared/tar.txt', 'tardir1/file1.txt', 'shared/dir.txt', 'dir1/file1.txt')
    }

    @Test public void usesManifestFromJarTaskWhenMergingJars() {
        createDir('src1') {
            dir1 { file 'file1.txt' }
        }
        createDir('src2') {
            dir2 { file 'file2.txt' }
        }
        testFile('build.gradle') << '''
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

        inTestDirectory().withTasks('jar').run()
        TestFile jar = testFile('build/test.zip')

        def manifest = jar.manifest
        println manifest.mainAttributes
        assertThat(manifest.mainAttributes.getValue('attr'), equalTo('value'))

        TestFile expandDir = testFile('expected')
        jar.unzipTo(expandDir)
        expandDir.assertHasDescendants('dir1/file1.txt', 'dir2/file2.txt', 'META-INF/MANIFEST.MF')
    }

    private def createZip(String name, Closure cl) {
        TestFile zipRoot = testFile("${name}.root")
        TestFile zip = testFile(name)
        zipRoot.create(cl)
        zipRoot.zipTo(zip)
    }

    private def createTar(String name, Closure cl) {
        TestFile tarRoot = testFile("${name}.root")
        TestFile tar = testFile(name)
        tarRoot.create(cl)
        tarRoot.tarTo(tar)
    }

    private def createDir(String name, Closure cl) {
        TestFile root = testFile(name)
        root.create(cl)
    }
}
