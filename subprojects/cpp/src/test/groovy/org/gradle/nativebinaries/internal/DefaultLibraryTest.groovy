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

package org.gradle.nativebinaries.internal
import org.gradle.api.internal.file.FileResolver
import org.gradle.internal.reflect.DirectInstantiator
import spock.lang.Specification

class DefaultLibraryTest extends Specification {
    final library = new DefaultLibrary(new NativeBuildComponentIdentifier("project-path", "someLib"), new DirectInstantiator(), Stub(FileResolver))

    def "has useful string representation"() {

        expect:
        library.toString() == "library 'someLib'"
    }

    def "can use shared variant as requirement"() {
        when:
        def requirement = library.shared

        then:
        requirement.projectPath == 'project-path'
        requirement.libraryName == 'someLib'
        requirement.linkage == 'shared'
    }

    def "can use static variant as requirement"() {
        when:
        def requirement = library.static

        then:
        requirement.projectPath == 'project-path'
        requirement.libraryName == 'someLib'
        requirement.linkage == 'static'
    }

    }
