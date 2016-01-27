/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.plugins.ide.idea.model

import spock.lang.Specification

class ModuleLibraryTest extends Specification {
    def equality() {
        expect:
        createModuleLibraryWithScope(null).equals(createModuleLibraryWithScope("COMPILE"))
        createModuleLibraryWithScope("").equals(createModuleLibraryWithScope("COMPILE"))
        createModuleLibraryWithScope("COMPILE").equals(createModuleLibraryWithScope(null))
        createModuleLibraryWithScope("COMPILE").equals(createModuleLibraryWithScope(""))
        createModuleLibraryWithScope("").equals(createModuleLibraryWithScope(""))
        !createModuleLibraryWithScope("RUNTIME").equals(createModuleLibraryWithScope("COMPILE"))
        !createModuleLibraryWithScope("").equals(createModuleLibraryWithScope("RUNTIME"))
        !createModuleLibraryWithScope("RUNTIME").equals(createModuleLibraryWithScope(""))
    }

    def hash() {
        expect:
        createModuleLibraryWithScope(null).hashCode() == createModuleLibraryWithScope("COMPILE").hashCode()
        createModuleLibraryWithScope("").hashCode() == createModuleLibraryWithScope("COMPILE").hashCode()
    }

    private ModuleLibrary createModuleLibraryWithScope(String scope) {
        new ModuleLibrary([] as Set, [] as Set, [] as Set, [] as Set, scope)
    }

    def shouldNotExportDependencies() {
        expect:
        !(createModuleLibraryWithScope("COMPILE").exported)
        !(createModuleLibraryWithScope("RUNTIME").exported)
        !(createModuleLibraryWithScope("").exported)
        !(createModuleLibraryWithScope(null).exported)
        !(createModuleLibraryWithScope("TEST").exported)
    }
}
