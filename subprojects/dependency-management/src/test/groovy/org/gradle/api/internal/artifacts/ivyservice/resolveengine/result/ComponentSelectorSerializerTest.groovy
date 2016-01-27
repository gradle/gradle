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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.result

import org.gradle.api.artifacts.component.LibraryComponentSelector
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.component.ProjectComponentSelector
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector
import org.gradle.internal.component.local.model.DefaultLibraryComponentSelector
import org.gradle.internal.component.local.model.DefaultProjectComponentSelector
import org.gradle.internal.serialize.SerializerSpec
import spock.lang.Unroll

public class ComponentSelectorSerializerTest extends SerializerSpec {
    ComponentSelectorSerializer serializer = new ComponentSelectorSerializer()

    def "throws exception if null is provided"() {
        when:
        serialize(null, serializer)

        then:
        Throwable t = thrown(IllegalArgumentException)
        t.message == 'Provided component selector may not be null'
    }

    def "serializes ModuleComponentSelector"() {
        given:
        ModuleComponentSelector selection = new DefaultModuleComponentSelector('group-one', 'name-one', 'version-one')

        when:
        ModuleComponentSelector result = serialize(selection, serializer)

        then:
        result.group == 'group-one'
        result.module == 'name-one'
        result.version == 'version-one'
    }

    def "serializes BuildComponentSelector"() {
        given:
        ProjectComponentSelector selection = new DefaultProjectComponentSelector(':myPath')

        when:
        ProjectComponentSelector result = serialize(selection, serializer)

        then:
        result.projectPath == ':myPath'
    }

    @Unroll
    def "serializes LibraryComponentSelector project #projectPath library #libraryName variant #variant"() {
        given:
        LibraryComponentSelector selection = new DefaultLibraryComponentSelector(projectPath, libraryName)

        when:
        LibraryComponentSelector result = serialize(selection, serializer)

        then:
        result.projectPath == projectPath
        result.libraryName == libraryName

        where:
        projectPath | libraryName
        ':myPath'   | null
        ':myPath'   | 'myLib'
        ':myPath'   | null
        ':myPath'   | 'myLib'
    }
}
