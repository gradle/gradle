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

package org.gradle.api.internal.file

import org.gradle.api.InvalidUserDataException
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class BasicFileResolverTest extends Specification {
    @Rule TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    def baseDir = tmpDir.createDir("teŝt dir")
    def resolver = new BasicFileResolver(baseDir)

    def "converts relative path"() {
        expect:
        resolver.transform("some-file") == baseDir.file("some-file")
        resolver.transform("../other-file") == baseDir.file("../other-file")
        resolver.transform(".") == baseDir
    }

    def "converts absolute path"() {
        def target = tmpDir.file("some-file")

        expect:
        resolver.transform(target.absolutePath) == target
    }

    def "converts file URI"() {
        def target = tmpDir.file("some-file")

        expect:
        resolver.transform(target.toURI().toASCIIString()) == target
    }

    def "does not convert http URI"() {
        when:
        resolver.transform("http://127.0.0.1")

        then:
        InvalidUserDataException e = thrown()
        e.message == "Cannot convert URL 'http://127.0.0.1' to a file."
    }
}
