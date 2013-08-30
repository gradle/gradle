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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.store

import org.gradle.api.internal.file.TmpDirTemporaryFileProvider
import spock.lang.Specification

class ResolutionResultsStoreFactoryTest extends Specification {

    def f = new ResolutionResultsStoreFactory(new TmpDirTemporaryFileProvider())

    def "provides binary stores"() {
        def store1 = f.createBinaryStore("1")
        def store2 = f.createBinaryStore("2")

        expect:
        store1 != store2
        store1 == f.createBinaryStore("1")
    }

    def "provides caches"() {
        expect:
        f.createNewModelCache("x").load({"x"} as org.gradle.internal.Factory) == "x"
        f.createNewModelCache("y").load({"y"} as org.gradle.internal.Factory) == "y"
        f.createNewModelCache("y").load({"yyyy"} as org.gradle.internal.Factory) == "y"

        f.createOldModelCache("x").load({"x"} as org.gradle.internal.Factory) == "x"
        f.createOldModelCache("y").load({"y"} as org.gradle.internal.Factory) == "y"
        f.createOldModelCache("y").load({"yyyy"} as org.gradle.internal.Factory) == "y"
    }
}
