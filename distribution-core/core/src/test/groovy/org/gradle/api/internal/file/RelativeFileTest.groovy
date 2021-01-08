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

import org.gradle.api.file.RelativePath
import spock.lang.Specification

class RelativeFileTest extends Specification {
    def "can get base directory of relative file" () {
        File file = new File("/some/relatively/long/path/to/a/file")
        RelativePath relativePath = RelativePath.parse(true, "to/a/file")
        RelativeFile relativeFile = new RelativeFile(file, relativePath)

        expect:
        relativeFile.getBaseDir() == new File("/some/relatively/long/path")
    }

    def "base directory is null if either file or relativepath are null" () {
        RelativeFile relativeFile = new RelativeFile(file, relativePath)

        expect:
        relativeFile.getBaseDir() == null

        where:
        file                   | relativePath
        new File("/some/path") | null
        null                   | RelativePath.parse(true, "some/path")
    }
}
