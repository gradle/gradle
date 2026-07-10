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

package org.gradle.api.internal.file

import org.gradle.api.internal.file.archive.compression.Bzip2Archiver
import org.gradle.api.internal.file.archive.compression.GzipArchiver
import org.gradle.api.resources.internal.LocalResourceAdapter
import spock.lang.Specification

class MaybeCompressedFileResourceTest extends Specification {

    def "understands file extensions"() {
        expect:
        def fileResource = this.fileResource("foo")
        new MaybeCompressedFileResource(fileResource).resource == fileResource
        new MaybeCompressedFileResource(this.fileResource("foo.tgz")).resource instanceof GzipArchiver
        new MaybeCompressedFileResource(this.fileResource("foo.gz")).resource instanceof GzipArchiver
        new MaybeCompressedFileResource(this.fileResource("foo.bz2")).resource instanceof Bzip2Archiver
        new MaybeCompressedFileResource(this.fileResource("foo.tbz2")).resource instanceof Bzip2Archiver
    }

    def "passes through GzipArchiver resources called #name"() {
        given:
        def resource = new GzipArchiver(fileResource(name))
        def maybeCompressed = new MaybeCompressedFileResource(resource)

        expect:
        maybeCompressed.resource == resource

        where:
        name << [ "foo", "foo.tgz", "foo.gz" ]
    }

    def "passes through Bzip2Archiver resources called #name"() {
        given:
        def resource = new Bzip2Archiver(fileResource(name))
        def maybeCompressed = new MaybeCompressedFileResource(resource)

        expect:
        maybeCompressed.resource == resource

        where:
        name << [ "foo", "foo.bz2", "foo.tbz2" ]
    }

    def fileResource(String fileName) {
        return new LocalResourceAdapter(TestFiles.fileRepository().localResource(new File(fileName)))
    }
}
