/*
 * Copyright 2023 the original author or authors.
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
import org.gradle.internal.hash.DefaultFileHasher
import org.gradle.internal.hash.DefaultStreamHasher
import org.gradle.internal.hash.FileHasher

class ReuseArchiveIntegrationTest  extends AbstractIntegrationSpec {

    /*
     * This is a pre-existing issue: What do you do if creating a cache using a pre-existing directory
     * happens to contain content with the same name as content you're trying to cache?  The content used
     * by the cache no longer agrees with the content coming from the zip itself.
     */
    def "pre-existing content in cache dir with same hash is okay"() {
        file("contents/hello.txt") << "hello"
        file("contents").zipTo(file("hello.zip"))
        FileHasher hasher = new DefaultFileHasher(new DefaultStreamHasher())
        def hash = hasher.hash(file("hello.zip"))
        def cachedFile = file("build/tmp/.cache/expanded/zip_${hash}/hello.txt")
        def otherFile = file("build/tmp/.cache/expanded/zip_${hash}/other.txt")

        buildFile << """
            abstract class CopyAndList extends DefaultTask {
                @Inject
                abstract FileSystemOperations getFileSystemOperations()

                @InputFiles
                abstract ConfigurableFileCollection getUnzipped()

                @Inject
                abstract ProjectLayout getLayout()

                @TaskAction
                void copyAndList() {
                    getUnzipped().getFiles().each {
                        println it.absolutePath + ": " + it.text
                    }
                    getFileSystemOperations().copy {
                        from getUnzipped()
                        into layout.buildDirectory.dir("extract")
                    }
                }
            }
            task extract2(type: CopyAndList) {
                unzipped.from(zipTree(file("hello.zip")))
            }
        """
        when:
        succeeds("extract")
        then:
        file("build/extract/hello.txt").text == "hello"
        cachedFile.assertExists()
        otherFile.assertDoesNotExist()

        when:
        // write into the directory used by the extracted zip file
        cachedFile.text = "some incorrect pre-existing pre-expanded content"
        otherFile.touch()

        and:
        succeeds "extract"
        then:
        // the file is not extracted again and retains the modified content
        cachedFile.text == "some incorrect pre-existing pre-expanded content"
        otherFile.assertExists()
        // users of the zipTree still see the zip's contents
        file("build/extract/hello.txt").text == "hello"
    }
}
