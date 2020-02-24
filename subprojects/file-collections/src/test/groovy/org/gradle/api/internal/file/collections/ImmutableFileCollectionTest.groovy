/*
 * Copyright 2020 the original author or authors.
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


import org.gradle.util.UsesNativeServices
import spock.lang.Specification

@UsesNativeServices
class ImmutableFileCollectionTest extends Specification {
    def 'can create empty collection'() {
        ImmutableFileCollection collection1 = ImmutableFileCollection.of()
        ImmutableFileCollection collection2 = ImmutableFileCollection.of(new File[0])

        expect:
        collection1.files.size() == 0
        collection2.files.size() == 0
    }

    def 'empty collections are fixed instance'() {
        ImmutableFileCollection collection1 = ImmutableFileCollection.of()
        ImmutableFileCollection collection2 = ImmutableFileCollection.of()
        ImmutableFileCollection collection3 = ImmutableFileCollection.of(new File[0])

        expect:
        collection1.is(collection2)
        collection1.is(collection3)
    }

}
