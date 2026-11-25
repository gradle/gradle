/*
 * Copyright 2026 the original author or authors.
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

import spock.lang.Specification

class SubtractingFileCollectionTest extends Specification {
    def a = new File("a").absoluteFile
    def b = new File("b").absoluteFile
    def c = new File("c").absoluteFile

    def "can replace the left or the right collection"() {
        def left = TestFiles.fixed(a, b)
        def right = TestFiles.fixed(b)
        def collection = left.minus(right) as FileCollectionInternal

        expect:
        collection.replace(TestFiles.fixed(c), { throw new UnsupportedOperationException() }).is(collection)
        collection.replace(collection, { TestFiles.fixed(c) }).files == [c] as Set
        collection.replace(left, { TestFiles.fixed(a, b, c) }).files == [a, c] as Set
        collection.replace(right, { TestFiles.fixed(a) }).files == [b] as Set
    }
}
