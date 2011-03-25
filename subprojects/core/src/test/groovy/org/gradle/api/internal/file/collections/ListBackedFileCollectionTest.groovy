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
package org.gradle.api.internal.file.collections

import org.junit.Test
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*
import spock.lang.Specification

class ListBackedFileCollectionTest extends Specification {

    public void hasUsefulDisplayName() {
        def testFile = new File('test-file')
        def testFile2 = new File('test-file2')

        expect:
        new ListBackedFileCollection().displayName == "empty file collection"
        new ListBackedFileCollection(testFile).displayName == "file '$testFile'"
        new ListBackedFileCollection(testFile, testFile2).displayName == "files '$testFile', '$testFile2'"
    }

    @Test
    public void containsSpecifiedFiles() {
        def testFile = new File('test-file')

        expect:
        new ListBackedFileCollection(testFile).files == [testFile] as Set
    }
}
