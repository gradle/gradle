/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.vfs.impl


import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class DefaultFileSystemAccessConcurrencyTest extends AbstractFileSystemAccessTest {

    def "parallel invalidation yields correct results"() {
        def dir = temporaryFolder.createDir("some/deep/hierarchy")
        (1..1000).each {
            def subdir = dir.file(it)
            subdir.file("in-dir.txt").createFile()
        }

        allowFileSystemAccess(true)
        read(dir)
        def executorService = Executors.newFixedThreadPool(100)

        when:
        (1..1000).each { num ->
            executorService.submit({
                def locationToUpdate = dir.file(num).file("in-dir.txt")
                fileSystemAccess.write([locationToUpdate.absolutePath]) {
                    locationToUpdate.text = "updated"
                }
            })
        }
        executorService.awaitTermination(5, TimeUnit.SECONDS)
        then:
        (1..1000).each { num ->
            def updatedLocation = dir.file(num).file("in-dir.txt")
            assertIsFileSnapshot(read(updatedLocation), updatedLocation)
        }

        cleanup:
        executorService.shutdown()
    }
}
