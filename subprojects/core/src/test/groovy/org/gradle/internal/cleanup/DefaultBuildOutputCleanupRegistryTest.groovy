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
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.file.FileResolver
import org.gradle.util.UsesNativeServices
import spock.lang.Specification

@UsesNativeServices
class DefaultBuildOutputCleanupRegistryTest extends Specification {

    def fileResolver = Mock(FileResolver)
    def registry = new DefaultBuildOutputCleanupRegistry(fileResolver)

    def "no outputs are registered by default"() {
        expect:
        registry.outputs.empty
    }

    def "can register files, directories and file collections"() {
        given:
        def dir1 = new File('dir1')
        File file1 = new File('someDir/test1.txt')
        def outputFiles = Mock(FileCollection)


        when:
        registry.registerOutputs(dir1)
        registry.registerOutputs(file1)
        registry.registerOutputs(outputFiles)

        then:
        1 * fileResolver.resolveFiles(dir1) >> Mock(FileCollectionInternal)
        1 * fileResolver.resolveFiles(file1) >> Mock(FileCollectionInternal)
        1 * fileResolver.resolveFiles(outputFiles) >> Mock(FileCollectionInternal)
        registry.outputs.sources.size() == 3
    }
}
