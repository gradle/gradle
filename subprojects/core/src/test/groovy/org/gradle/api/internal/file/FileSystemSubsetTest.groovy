/*
 * Copyright 2015 the original author or authors.
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

import org.gradle.api.tasks.util.PatternSet
import org.gradle.util.UsesNativeServices
import spock.lang.Specification

@UsesNativeServices
class FileSystemSubsetTest extends Specification {

    def "root of directory tree is contained"() {
        when:
        def f = new File('foo').absoluteFile
        def s = FileSystemSubset.builder().add(f).build()

        then:
        s.contains(f)
        s.contains(new File(f, "sub"))
        !s.contains(f.parentFile)
    }

    def "can be empty"() {
        expect:
        FileSystemSubset.builder().build().empty
        !FileSystemSubset.builder().add(new File("f")).build().empty
    }

    def "can filter tree"() {
        when:
        def f = new File('foo').absoluteFile
        def s = FileSystemSubset.builder().add(f, new PatternSet().include("*.txt")).build()

        then:
        s.contains(f)
        s.contains(new File(f, "some.txt"))
        !s.contains(new File(f, "some.img"))
    }

    def "can compose"() {
        when:
        def f = new File('foo').absoluteFile
        def s = FileSystemSubset.builder()
            .add(f, new PatternSet().include("*.txt"))
            .add(f)
            .build()

        then:
        s.contains(f)
        s.contains(new File(f, "some.txt"))
        s.contains(new File(f, "some.img"))
    }
}
