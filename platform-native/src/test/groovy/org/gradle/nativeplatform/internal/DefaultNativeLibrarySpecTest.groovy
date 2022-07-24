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

package org.gradle.nativeplatform.internal

import org.gradle.nativeplatform.NativeLibrarySpec
import org.gradle.platform.base.component.BaseComponentFixtures
import org.gradle.platform.base.internal.DefaultComponentSpecIdentifier
import spock.lang.Specification

class DefaultNativeLibrarySpecTest extends Specification {
    final library = BaseComponentFixtures.create(NativeLibrarySpec, DefaultNativeLibrarySpec, new DefaultComponentSpecIdentifier("project-path", "someLib"))

    def "has useful string representation"() {
        expect:
        library.toString() == "native library 'someLib'"
        library.displayName == "native library 'someLib'"
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

    def "can use api linkage as requirement"() {
        when:
        def requirement = library.api

        then:
        requirement.projectPath == 'project-path'
        requirement.libraryName == 'someLib'
        requirement.linkage == 'api'
    }
}
