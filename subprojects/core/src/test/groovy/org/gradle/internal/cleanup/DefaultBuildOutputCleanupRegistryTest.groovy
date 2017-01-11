/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.cleanup

import org.gradle.api.file.FileCollection
import spock.lang.Specification

class DefaultBuildOutputCleanupRegistryTest extends Specification {

    def registry = new DefaultBuildOutputCleanupRegistry()
    def outputFiles = Mock(FileCollection)

    def "no outputs are registered by default"() {
        expect:
        registry.outputs.empty
    }

    def "can register files and directories"() {
        given:
        File dir1 = new File('dir1')
        File dir2 = new File('dir2')
        File file1 = new File('someDir/test1.txt')
        File file2 = new File('someDir/test2.txt')
        Set<File> files = [file2, dir2]

        when:
        registry.registerOutputs(dir1, file1)

        then:
        registry.outputs == [dir1, file1] as Set

        when:
        registry.registerOutputs(outputFiles)

        then:
        1 * outputFiles.files >> files
        registry.outputs == [dir1, file1] + files as Set
    }

    def "can remove registered files and directories"() {
        given:
        File dir = new File('dir')
        File file = new File('someDir/test.txt')

        when:
        registry.registerOutputs(dir, file)

        then:
        registry.outputs == [dir, file] as Set

        when:
        registry.outputs.removeAll([dir, file])

        then:
        registry.outputs.empty
    }

    def "only registers unique files or directories"() {
        given:
        File dir = new File('dir')
        File file = new File('someDir/test.txt')

        when:
        registry.registerOutputs(dir, file)

        then:
        registry.outputs == [dir, file] as Set

        when:
        registry.registerOutputs(dir, file)

        then:
        registry.outputs == [dir, file] as Set
    }
}
