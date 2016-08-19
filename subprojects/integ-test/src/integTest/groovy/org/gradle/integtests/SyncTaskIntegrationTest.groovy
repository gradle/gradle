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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class SyncTaskIntegrationTest extends AbstractIntegrationSpec {

    def copiesFilesAndRemovesExtraFilesFromDestDir() {
        given:
        file('source').create {
            dir1 { file 'file1.txt' }
            dir2 {
                subdir { file 'file2.txt' }
                file 'file3.txt'
            }
        }
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
    }

    def preserveInDestinationKeepsSpecifiedFilesInDestDir() {
        given:
        file('source').create {
            dir1 { file 'file1.txt' }
            dir2 {
                subdir { file 'file2.txt' }
                file 'file3.txt'
            }
        }
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
}
