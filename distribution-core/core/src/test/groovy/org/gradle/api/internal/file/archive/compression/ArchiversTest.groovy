/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.api.internal.file.archive.compression

import org.gradle.api.internal.file.TestFiles
import org.gradle.api.resources.internal.LocalResourceAdapter
import spock.lang.Specification

class ArchiversTest extends Specification {

    def "archivers have unique URIs"() {
        when:
        def file = new File("/some/file")

        def resource = new LocalResourceAdapter(TestFiles.fileRepository().localResource(file))
        def bzip2 = new Bzip2Archiver(resource)
        def gzip = new GzipArchiver(resource)

        then:
        resource.URI != bzip2.URI
        bzip2.URI != gzip.URI
        gzip.URI != resource.URI
    }
}
