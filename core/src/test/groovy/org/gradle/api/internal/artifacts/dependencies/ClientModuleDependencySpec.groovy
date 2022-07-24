/*
 * Copyright 2014 the original author or authors.
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



package org.gradle.api.internal.artifacts.dependencies

import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ModuleDependency

class ClientModuleDependencySpec extends AbstractModuleDependencySpec {

    protected ExternalModuleDependency createDependency(String group, String name, String version, String configuration) {
        new DefaultClientModule(group, name, version, configuration)
    }

    def "not equal with different module dependencies"() {
        when:
        def dep1 = createDependency("group1", "name1", "version1", null)
        def dep2 = createDependency("group1", "name1", "version1", null)
        dep2.addDependency(Mock(ModuleDependency))

        then:
        dep1 != dep2
        !dep1.contentEquals(dep2)
        !dep2.contentEquals(dep1)
    }
}
