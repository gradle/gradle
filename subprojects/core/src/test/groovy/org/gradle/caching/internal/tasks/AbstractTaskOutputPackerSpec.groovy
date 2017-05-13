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

package org.gradle.caching.internal.tasks

import org.gradle.api.internal.tasks.ResolvedTaskOutputFilePropertySpec
import org.gradle.caching.internal.tasks.origin.TaskOutputOriginReader
import org.gradle.caching.internal.tasks.origin.TaskOutputOriginWriter
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

@CleanupTestDirectory(fieldName = "tempDir")
abstract class AbstractTaskOutputPackerSpec extends Specification {
    @Rule
    TestNameTestDirectoryProvider tempDir = new TestNameTestDirectoryProvider()
    def readOrigin = Stub(TaskOutputOriginReader)
    def writeOrigin = Stub(TaskOutputOriginWriter)

    abstract TaskOutputPacker getPacker()

    def pack(OutputStream output, TaskOutputOriginWriter writeOrigin = this.writeOrigin, ResolvedTaskOutputFilePropertySpec... propertySpecs) {
        pack(output, writeOrigin, propertySpecs as SortedSet)
    }

    def pack(OutputStream output, TaskOutputOriginWriter writeOrigin = this.writeOrigin, SortedSet<ResolvedTaskOutputFilePropertySpec> propertySpecs) {
        packer.pack(propertySpecs, output, writeOrigin)
    }

    def unpack(InputStream input, TaskOutputOriginReader readOrigin = this.readOrigin, ResolvedTaskOutputFilePropertySpec... propertySpecs) {
        unpack(input, readOrigin, propertySpecs as SortedSet)
    }

    def unpack(InputStream input, TaskOutputOriginReader readOrigin = this.readOrigin, SortedSet<ResolvedTaskOutputFilePropertySpec> propertySpecs) {
        packer.unpack(propertySpecs, input, readOrigin)
    }
}
