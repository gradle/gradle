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

import org.gradle.api.internal.file.TestFiles
import spock.lang.Specification

class DefaultBuildOutputCleanupRegistryTest extends Specification {

    def fileCollectionFactory = TestFiles.fileCollectionFactory()
    def registry = new DefaultBuildOutputCleanupRegistry(fileCollectionFactory)

    def "can register files, directories and file collections"() {
        given:
        def dir1 = file('dir1')
        File file1 = file('someDir/test1.txt')
        File outputFile = file('someDir/test2.txt')
        def outputFiles = TestFiles.fixed(outputFile)


        when:
        registry.registerOutputs(dir1)
        registry.registerOutputs(file1)
        registry.registerOutputs(outputFiles)

        then:
        registry.isOutputOwnedByBuild(dir1)
        registry.isOutputOwnedByBuild(file1)
        registry.isOutputOwnedByBuild(outputFile)
    }

    def "determines files which are owned by the build"() {
        registry.registerOutputs(file('build/outputs'))
        registry.registerOutputs(file('build/outputs/other'))
        registry.registerOutputs(file('outputs'))

        expect:
        registry.isOutputOwnedByBuild(file('build/outputs'))
        registry.isOutputOwnedByBuild(file('build/outputs/some-dir/other-dir'))
        registry.isOutputOwnedByBuild(file('build/outputs/other'))
        registry.isOutputOwnedByBuild(file('build/outputs/other/even-an-other-dir'))
        registry.isOutputOwnedByBuild(file('outputs/even-an-other-dir'))
        !registry.isOutputOwnedByBuild(file('build'))
        !registry.isOutputOwnedByBuild(file('output'))
        !registry.isOutputOwnedByBuild(file('build/output'))
        !registry.isOutputOwnedByBuild(file('different-file/build/outputs'))
    }

    File file(String fileName) {
        new File(fileName).absoluteFile
    }

}
