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

class ModuleDependencyTest extends Specification {
    def equality() {
        expect:
        new ModuleDependency("a", null).equals(new ModuleDependency("a", "COMPILE"))
        new ModuleDependency("a", "").equals(new ModuleDependency("a", "COMPILE"))
        new ModuleDependency("a", "COMPILE").equals(new ModuleDependency("a", ""))
        new ModuleDependency("a", "COMPILE").equals(new ModuleDependency("a", null))
        new ModuleDependency("a", "").equals(new ModuleDependency("a", ""))
        !new ModuleDependency("a", "").equals(new ModuleDependency("b", ""))
        !new ModuleDependency("a", "RUNTIME").equals(new ModuleDependency("a", "COMPILE"))
        !new ModuleDependency("a", "").equals(new ModuleDependency("a", "RUNTIME"))
        !new ModuleDependency("a", "RUNTIME").equals(new ModuleDependency("a", ""))
    }

    def hash() {
        expect:
        new ModuleDependency("a", null).hashCode() == new ModuleDependency("a", "COMPILE").hashCode()
        new ModuleDependency("a", "").hashCode() == new ModuleDependency("a", "COMPILE").hashCode()
    }

    def shouldNotExportDependencies() {
        expect:
        !(new ModuleDependency("a", "COMPILE").exported)
        !(new ModuleDependency("a", "RUNTIME").exported)
        !(new ModuleDependency("a", "").exported)
        !(new ModuleDependency("a", null).exported)
        !(new ModuleDependency("a", "TEST").exported)
    }
}
