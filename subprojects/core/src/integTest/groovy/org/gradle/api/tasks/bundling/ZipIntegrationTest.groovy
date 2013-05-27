/*
 * Copyright 2013 the original author or authors.
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

import java.util.zip.ZipFile

import static org.junit.Assert.assertEquals;

class ZipIntegrationTest  extends AbstractIntegrationSpec {

    def ensureDuplicatesIncludedWithoutWarning() {
        given:
        createDir('dir1', {
            file 'file1.txt'
        })
        createDir('dir2', {
            file 'file2.txt'
        })
        createDir('dir3', {
            file 'file1.txt'
        })
        buildFile << '''
            task zip(type: Zip) {
                from 'dir1'
                from 'dir2'
                from 'dir3'
                destinationDir = buildDir
                archiveName = 'test.zip'
            }
            '''
        when:
        run 'zip'

        then:
        assertZipContains('build/test.zip', [ 'file1.txt', 'file1.txt', 'file2.txt' ])
    }

    def ensureDuplicatesCanBeExcluded() {
        given:
        createDir('dir1', {
            file 'file1.txt'
        })
        createDir('dir2', {
            file 'file2.txt'
        })
        createDir('dir3', {
            file 'file1.txt'
        })
        buildFile << '''
            task zip(type: Zip) {
                from 'dir1'
                from 'dir2'
                from 'dir3'
                destinationDir = buildDir
                archiveName = 'test.zip'
                eachFile { it.duplicatesStrategy = 'exclude' }
            }
            '''
        when:
        run 'zip'

        then:
        assertZipContains('build/test.zip', [ 'file1.txt', 'file2.txt' ])
    }



    def assertZipContains(zipfile, files) {
        def entries = new ZipFile(file(zipfile)).entries();
        def list = []
        while (entries.hasMoreElements()) {
            def entry = entries.nextElement()
            list += entry.getName();
        }
        assertEquals(files.sort(), list.sort())
        return this
    }
}
