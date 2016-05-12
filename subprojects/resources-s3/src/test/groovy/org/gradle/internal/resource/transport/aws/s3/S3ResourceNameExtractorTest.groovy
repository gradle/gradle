/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.internal.resource.transport.aws.s3

import spock.lang.Specification

class S3ResourceNameExtractorTest extends Specification {
    def "should extract file name from s3 listing"() {
        expect:
        S3ResourceNameExtractor.extractResourceName(listing) == expected

        where:
        listing         | expected
        '/a/b/file.pom' | 'file.pom'
        '/file.pom'     | 'file.pom'
        '/file.pom'     | 'file.pom'
        '/SNAPSHOT/'    | null
        '/SNAPSHOT/bin' | null
        '/'             | null
    }

    def "should extract directory name from s3 common prefix"() {
        expect:
        S3ResourceNameExtractor.extractDirectoryName(commonPrefix) == expected

        where:
        commonPrefix                | expected
        '/a/b/directory.with.dot/'  | 'directory.with.dot/'
        '/directory.with.dot/'      | 'directory.with.dot/'
        '/directoryWithoutDot/'     | 'directoryWithoutDot/'
        '/directory.with.dot'       | null
        '/'                         | null
    }
}