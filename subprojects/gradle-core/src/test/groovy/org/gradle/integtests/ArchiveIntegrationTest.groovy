package org.gradle.integtests

import org.junit.Test

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
    into buildDir
}
'''

        inTestDirectory().withTasks('copy').run()

        testFile('build').assertHasDescendants('subdir1/file1.txt', 'subdir2/file2.txt')
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
    into buildDir
}
'''

        inTestDirectory().withTasks('copy').run()

        testFile('build').assertHasDescendants('subdir1/file1.txt', 'subdir2/file2.txt')
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
    fileSet(dir: 'test')
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