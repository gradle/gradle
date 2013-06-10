/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.nativecode.base.internal

import org.gradle.api.file.FileCollection
import org.gradle.api.internal.file.FileResolver
import org.gradle.nativecode.base.NativeDependencySet
import org.gradle.nativecode.base.SharedLibraryBinary
import org.gradle.nativecode.base.StaticLibraryBinary
import spock.lang.Specification

class DefaultLibraryTest extends Specification {
    def "has useful string representation"() {
        def library = new DefaultLibrary("someLib", Stub(FileResolver))

        expect:
        library.toString() == "library 'someLib'"
    }

    def "can use shared and static variants as dependencies"() {
        def library = new DefaultLibrary("someLib", Stub(FileResolver))
        def sharedLinkFiles = Stub(FileCollection)
        def staticLinkFiles = Stub(FileCollection)
        def sharedDependency = Stub(NativeDependencySet)
        def staticDependency = Stub(NativeDependencySet)
        def sharedBinary = Stub(SharedLibraryBinary)
        def staticBinary = Stub(StaticLibraryBinary)

        given:
        library.binaries.add(sharedBinary)
        library.binaries.add(staticBinary)

        and:
        sharedDependency.linkFiles >> sharedLinkFiles
        staticDependency.linkFiles >> staticLinkFiles
        sharedBinary.asNativeDependencySet >> sharedDependency
        staticBinary.asNativeDependencySet >> staticDependency

        expect:
        library.shared.linkFiles == sharedLinkFiles
        library.static.linkFiles == staticLinkFiles
    }
}
