/*
 * Copyright 2009 the original author or authors.
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

import groovy.transform.NotYetImplemented
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Issue

class SyncTaskIntegrationTest extends AbstractIntegrationSpec {

    def 'copies files and removes extra files from destDir'() {
        given:
        defaultSourceFileTree()
        file('dest').create {
            file 'extra.txt'
            extraDir { file 'extra.txt' }
            dir1 {
                file 'extra.txt'
                extraDir { file 'extra.txt' }
            }
            someOtherEmptyDir {}
        }

        buildScript '''
            task sync(type: Sync) {
                into 'dest'
                from 'source'
            }
        '''.stripIndent()

        when:
        run 'sync'

        then:
        file('dest').assertHasDescendants(
                'dir1/file1.txt',
                'dir2/subdir/file2.txt',
                'dir2/file3.txt'
        )
        !file('dest/someOtherEmptyDir').exists()
        file('dest/emptyDir').exists()
    }

    def 'preserve keeps specified files in destDir'() {
        given:
        defaultSourceFileTree()
        file('dest').create {
            file 'extra.txt'
            extraDir { file 'extra.txt' }
            dir1 {
                file 'extra.txt'
                extraDir { file 'extra.txt' }
            }
        }

        buildScript '''
            task sync(type: Sync) {
                into 'dest'
                from 'source'
                preserve {
                  include 'extraDir/**'
                  include 'dir1/**'
                  exclude 'dir1/extra.txt'
                }
            }
        '''.stripIndent()

        when:
        run 'sync'

        then:
        file('dest').assertHasDescendants(
            'dir1/file1.txt',
            'dir2/subdir/file2.txt',
            'dir2/file3.txt',
            'extraDir/extra.txt',
            'dir1/extraDir/extra.txt',
        )
    }

    def 'only excluding non-preserved files works as expected'() {
        given:
        defaultSourceFileTree()
        file('dest').create {
            somePreservedDir {
                file 'preserved.txt'
                file 'also-not-preserved.txt'
            }
            someOtherDir {
                file 'preserved.txt'
                file 'not-preserved.txt'
            }
        }

        buildScript '''
            task sync(type: Sync) {
                from 'source'
                into 'dest'
                preserve {
                    exclude 'someOtherDir/not-preserved.txt'
                    exclude 'somePreservedDir/also-not-preserved.txt'
                }
            }
        '''.stripIndent()

        when:
        run 'sync'

        then:
        file('dest').allDescendants() == [
            'dir1/file1.txt',
            'dir2/subdir/file2.txt',
            'dir2/file3.txt',
            'someOtherDir/preserved.txt',
            'somePreservedDir/preserved.txt'
        ] as Set
    }

    def 'sync is up to date when only changing preserved files'() {
        given:
        file('source').create {
            file 'not-preserved.txt'
        }

        file('dest').create {
            file 'preserved.txt'
        }

        buildScript '''
            task sync(type: Sync) {
                from 'source'
                into 'dest'
                preserve {
                    include 'preserved.txt'
                }
            }
        '''.stripIndent()

        when:
        run 'sync'

        then:
        file('dest').allDescendants() == ['not-preserved.txt', 'preserved.txt'] as Set
        noneSkipped()

        when:
        file('dest/preserved.txt').text = 'Changed!'
        run 'sync'

        then:
        skipped ':sync'

        when:
        file('dest/not-preserved.txt').text = 'Changed!'
        run 'sync'

        then:
        executedAndNotSkipped ':sync'
    }

    @NotYetImplemented
    def 'sync is not up to date when files are added to the destination dir'() {
        given:
        defaultSourceFileTree()
        file('dest').create {}

        buildScript '''
            task sync(type: Sync) {
                from 'source'
                into 'dest'
            }
        '''.stripIndent()

        when:
        run 'sync'

        then:
        noneSkipped()

        when:
        file('dest/new-file.txt').text = 'Created!'
        run 'sync'

        then:
        noneSkipped()
    }

    @NotYetImplemented
    def 'sync is not up to date when the preserve filter is changed'() {
        given:
        defaultSourceFileTree()
        file('dest').create {
            preserved { file('some-preserved-file.txt') }
        }

        buildScript '''
            task sync(type: Sync) {
                from 'source'
                into 'dest'
                preserve {
                    include 'preserved'
                }
            }
        '''.stripIndent()

        when:
        run 'sync'

        then:
        noneSkipped()
        file('dest/preserved').exists()

        when:
        buildScript '''
            task sync(type: Sync) {
                from 'source'
                into 'dest'
            }
        '''.stripIndent()
        run 'sync'

        then:
        noneSkipped()
        !file('dest/preserved').exists()
    }

    def 'default excludes are removed with non-preserved directories'(String preserved) {
        given:
        defaultSourceFileTree()
        file('dest').create {
            some {
                '.git' { }
            }
            out {
                '.git' {
                    file 'config'
                }
                file 'some.txt'
            }
        }

        buildScript """
            task sync(type: Sync) {
                from 'source'
                into 'dest'
                preserve {
                  ${preserved}
                }
            }
        """.stripIndent()

        when:
        run 'sync'

        then:
        file('dest/out/.git').isDirectory()
        !file('dest/some').exists()

        where:
        preserved << ["include 'out/some.txt'", "exclude 'some'"]
    }

    def 'empty directories can be preserved and synced'() {
        given:
        defaultSourceFileTree()
        file('dest').create {
            preservedDir { }
            nonPreservedDir { }
        }

        buildScript '''
            task sync(type: Sync) {
                from 'source'
                into 'dest'
                preserve {
                    include 'preservedDir'
                }
            }
        '''.stripIndent()

        when:
        run 'sync'

        then:
        file('dest/preservedDir').isDirectory()
        file('dest/emptyDir').isDirectory()
        !file('dest/nonPreservedDir').isDirectory()
    }

    def "sync action"() {
        given:
        defaultSourceFileTree()
        file('dest').create {
            file 'extra1.txt'
            extraDir { file 'extra2.txt' }
        }
        buildScript '''
            task syncIt() {
                doLast {
                    project.sync {
                        from 'source'
                        into 'dest'
                    }
                }
            }
        '''.stripIndent()

        when:
        run 'syncIt'

        then:
        file('dest').assertHasDescendants(
            'dir1/file1.txt',
            'dir2/subdir/file2.txt',
            'dir2/file3.txt'
        )
        file('dest/emptyDir').exists()
        !file('dest/extra1.txt').exists()
        !file('dest/extraDir/extra2.txt').exists()
    }

    def "sync single files"() {
        given:
        file('source').create {
            file 'file1.txt'
            file 'file2.txt'
        }
        file('dest').create {
            file 'extra.txt'
        }
        buildScript '''
            task syncIt {
                doLast {
                    project.sync {
                        from 'source'
                        into 'dest'
                    }
                }
            }
        '''.stripIndent()

        when:
        run 'syncIt'

        then:
        file('dest').assertHasDescendants(
            'file1.txt',
            'file2.txt',
        )
        !file('dest/extra.txt').exists()
    }

    @Requires(TestPrecondition.WINDOWS)
    def "sync fails when unable to clean-up files"() {
        given:
        file('source').create {
            file 'file1.txt'
            file 'file2.txt'
        }
        file('dest').create {
            file 'extra.txt'
        }
        // Intentionally hold open a file
        def ins = new FileInputStream(file("dest/extra.txt"))
        buildScript '''
            task syncIt {
                doLast {
                    project.sync {
                        from 'source'
                        into 'dest'
                    }
                }
            }
        '''.stripIndent()

        expect:
        fails 'syncIt'
        ins.close()
    }

    @Issue("https://github.com/gradle/gradle/issues/9586")
    def "change in case of input files and folders will still syncs properly"() {
        given:
        buildFile << '''
            task syncIt(type: Sync) {
                from project.hasProperty("capitalizeFile") ? "FILE.TXT" : "file.txt"
                from project.hasProperty("capitalizeDir") ? "DIR" : "dir"
                into buildDir
            }
        '''

        def lowercaseFile = file('file.txt')
        lowercaseFile.text = 'file'

        file('dir').create {
            file('file2.txt') << 'file2'
            file('file3.txt') << 'file3'
            file('file4.txt') << 'file4'
            nestedDir {
                file('nestedDirFile.txt') << 'nestedDirFile'
            }
        }
        and:
        run 'syncIt'
        file('build').assertHasDescendants(
            'file.txt',
            'file2.txt',
            'file3.txt',
            'file4.txt',
            'nestedDir/nestedDirFile.txt'
        )

        and:
        def uppercaseFile = new File(lowercaseFile.parentFile, 'FILE.TXT') // make sure case is kept
        lowercaseFile.renameTo(uppercaseFile)
        uppercaseFile.text = 'FILE'
        assert uppercaseFile.canonicalFile.name == 'FILE.TXT'
        and:
        file('dir/file3.txt').delete()

        file('dir/file4.txt').delete()
        file('dir/File4.txt') << 'File4'

        when:
        succeeds('syncIt', '-PcapitalizeFile')
        then:
        executedAndNotSkipped ':syncIt'
        file('build/FILE.TXT').with {
            assert it.parentFile.list() != [].toArray()
            assert it.assertExists()
            assert it.text == 'FILE'
            assert it.canonicalFile.name == 'FILE.TXT'
        }
        and:
        file('build/file2.txt').assertExists()
        file('build/file3.txt').assertDoesNotExist()
        file('build/File4.txt').with {
            it.assertExists()
            it.canonicalFile.name == 'File4.txt'
        }

        when:
        file('dir').deleteDir()
        file('DIR').create {
            file('file4.txt') << 'file4'
            NESTEDDIR {
                file('NESTEDDIRFILE.TXT') << 'NESTEDDIRFILE'
            }
        }
        and:
        succeeds('sync', '-PcapitalizeDir')
        then:
        executedAndNotSkipped ':syncIt'
        file('build/file4.txt').canonicalFile.name == 'file4.txt'
        file('build/NESTEDDIR/NESTEDDIRFILE.TXT').with {
            assert it.exists(): it.parentFile.list()
            assert it.parentFile.canonicalFile.name == 'NESTEDDIR'
            assert it.canonicalFile.name == 'NESTEDDIRFILE.TXT'
            assert it.text == 'NESTEDDIRFILE'
        }
    }

    def "sync from file tree"() {
        given:
        file('source').create {
            file 'file1.txt'
            dir1 { file 'file2.txt' }
            ignore { file 'file3.txt' } // to be ignored
        }
        file('dest').create {
            file 'extra1.txt'
            dir1 { file 'extra2.txt' }
            dir2 { file 'extra3.txt' }
        }
        buildScript '''
        task syncIt {
            doLast {
                project.sync {
                    from fileTree(dir: 'source', excludes: ['**/ignore/**'], includes: ['*', '*/*'])
                    into 'dest'
                }
            }
        }
        '''.stripIndent()

        when:
        run 'syncIt'

        then:
        file('dest').assertHasDescendants(
            'file1.txt',
            'dir1/file2.txt',
        )
        !file('ignore/file3.txt').exists()
        !file('dest/extra1.txt').exists()
        !file('dest/dir1/extra2.txt').exists()
        !file('dest/dir2/extra3.txt').exists()
    }

    def "sync from file collection"() {
        given:
        file('source').create {
            file 'file1.txt'
            dir1 { file 'file2.txt' }
            ignore { file 'file3.txt' } // to be ignored
        }
        file('dest').create {
            file 'extra1.txt'
            dir1 { file 'extra2.txt' }
            dir2 { file 'extra3.txt' }
        }
        buildScript '''
            task syncIt {
                doLast {
                    project.sync {
                        from files('source')
                        into 'dest'
                        exclude '**/ignore/**'
                        exclude '*/*/*/**'
                    }
                }
            }
        '''.stripIndent()

        when:
        run 'syncIt'

        then:
        file('dest').assertHasDescendants(
            'file1.txt',
            'dir1/file2.txt',
        )
        !file('ignore/file3.txt').exists()
        !file('dest/extra1.txt').exists()
        !file('dest/dir1/extra2.txt').exists()
        !file('dest/dir2/extra3.txt').exists()
    }

    def "sync from composite file collection"() {
        given:
        file('source').create {
            file 'file1.txt'
            dir1 { file 'file2.txt' }
        }
        file('source2').create {
            file 'file3.txt'
            dir1 { file 'file4.txt' }
            ignore { file 'file5.txt' } // to be ignored
        }
        file('dest').create {
            file 'extra1.txt'
            dir1 { file 'extra2.txt' }
        }
        file('f.jar').touch()
        buildScript '''
            configurations { compile }
            dependencies { compile files('f.jar') }
            task syncIt {
                doLast {
                    project.sync {
                        from files('source')
                        from fileTree('source2') { exclude '**/ignore/**' } 
                        from configurations.compile
                        into 'dest'
                        include { fte -> fte.relativePath.segments.length < 3 && (fte.file.directory || fte.file.name.contains('f')) }
                    }
                }
            }
        '''.stripIndent()

        when:
        run 'syncIt'

        then:
        file('dest').assertHasDescendants(
            'file1.txt',
            'f.jar',
            'file3.txt',
            'dir1/file2.txt',
            'dir1/file4.txt',
        )
        !file('ignore/file5.txt').exists()
        !file('dest/extra1.txt').exists()
        !file('dest/dir1/extra2.txt').exists()
    }

    def defaultSourceFileTree() {
        file('source').create {
            dir1 { file 'file1.txt' }
            dir2 {
                subdir { file 'file2.txt' }
                file 'file3.txt'
            }
            emptyDir {}
        }
    }
}
