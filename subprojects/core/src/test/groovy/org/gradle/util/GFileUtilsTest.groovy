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

package org.gradle.util

import org.junit.Rule
import spock.lang.Specification

/**
 * by Szczepan Faber, created at: 2/28/12
 */
class GFileUtilsTest extends Specification {
    
    @Rule TemporaryFolder temp

    def "can read the file's tail"() {
        def f = temp.file("foo.txt") << """
one
two
three
"""
        when:
        def out = GFileUtils.tail(f, 2)

        then:
        out == """two
three
"""
    }

    def "createDirectory() succeeds if directory already exists"() {
        def dir = temp.createDir("foo")
        assert dir.exists()

        when:
        GFileUtils.createDirectory(dir)

        then:
        noExceptionThrown()
        dir.exists()
    }
}
