package org.gradle.integtests

import org.junit.Test
import static org.hamcrest.Matchers.*

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
                customName = 'test.zip'
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
                customName = 'test.jar'
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
                customName = 'test.tar'
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
                file 'ignored.xml'
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
                destinationDir = buildDir
                customName = 'test.zip'
            }
'''

        inTestDirectory().withTasks('zip').run()

        TestFile expandDir = testFile('expanded')
        testFile('build/test.zip').unzipTo(expandDir)
        expandDir.assertHasDescendants('prefix/dir1/renamed_file1.txt', 'prefix/renamed_file1.txt', 'prefix/dir2/renamed_file2.txt')
        expandDir.file('prefix/dir1/renamed_file1.txt').assertContents(equalTo('[abc]')) 
    }

    @Test public void canCreateATarArchive() {
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
                from 'test'
                include '**/*.txt'
                destinationDir = buildDir
                customName = 'test.tar'
            }
'''

        inTestDirectory().withTasks('tar').run()

        TestFile expandDir = testFile('expanded')
        testFile('build/test.tar').untarTo(expandDir)
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
                customName = 'test.jar'
            }
'''

        inTestDirectory().withTasks('jar').run()

        TestFile expandDir = testFile('expanded')
        testFile('build/test.jar').unzipTo(expandDir)
        expandDir.assertHasDescendants('META-INF/MANIFEST.MF', 'META-INF/file1.txt', 'META-INF/dir2/file2.txt', 'dir1/file1.txt')

        expandDir.file('META-INF/MANIFEST.MF').assertContents(equalTo('Manifest-Version: 1.0\r\n\r\n'))
    }

    @Test public void filtersDoNotAffectMetaInf() {
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

        testFile('build.gradle') << '''
            task jar(type: Jar) {
                from 'test'
                include '**/*.txt'
                metaInf {
                    from 'meta-inf'
                    include '**/*.xml'
                }
                destinationDir = buildDir
                customName = 'test.jar'
            }
'''

        inTestDirectory().withTasks('jar').run()

        TestFile expandDir = testFile('expanded')
        testFile('build/test.jar').unzipTo(expandDir)
        expandDir.assertHasDescendants('META-INF/MANIFEST.MF', 'META-INF/dir2/file2.xml', 'dir1/file1.txt')
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
                customName = 'test.war'
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
                customName = 'test.war'
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
    
    @Test public void canMergeArchivesIntoAnotherArchive() {
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
    customName = 'test.zip'
}
'''

        inTestDirectory().withTasks('zip').run()

        TestFile expandDir = testFile('expanded')
        testFile('build/test.zip').unzipTo(expandDir) 
        expandDir.assertHasDescendants('shared/zip.txt', 'zipdir1/file1.txt', 'shared/tar.txt', 'tardir1/file1.txt', 'shared/dir.txt', 'dir1/file1.txt')
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