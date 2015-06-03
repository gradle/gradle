/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.internal.component.local.model

import org.gradle.api.artifacts.component.LibraryComponentIdentifier
import org.gradle.api.artifacts.component.LibraryComponentSelector
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import spock.lang.Specification
import spock.lang.Unroll

import static org.gradle.util.Matchers.strictlyEquals

class DefaultLibraryComponentSelectorTest extends Specification {
    def "is instantiated with non-null constructor parameter values"() {
        when:
        LibraryComponentSelector defaultBuildComponentSelector = new DefaultLibraryComponentSelector(':myPath', 'myLib')

        then:
        defaultBuildComponentSelector.projectPath == ':myPath'
        defaultBuildComponentSelector.libraryName == 'myLib'
        defaultBuildComponentSelector.displayName == /project ':myPath' library 'myLib'/
        defaultBuildComponentSelector.toString() == /project ':myPath' library 'myLib'/
    }

    def "is instantiated with null project constructor parameter value"() {
        when:
        new DefaultLibraryComponentSelector(null, 'foo')

        then:
        Throwable t = thrown(AssertionError)
        t.message == 'project path cannot be null'
    }

    def "is instantiated with null library constructor parameter value"() {
        when:
        new DefaultLibraryComponentSelector(':foo', null)

        then:
        Throwable t = thrown(AssertionError)
        t.message == 'library name cannot be null'
    }

    @Unroll
    def "can compare with other instance (#projectPath,#libraryName)"() {
        expect:
        LibraryComponentSelector defaultBuildComponentSelector1 = new DefaultLibraryComponentSelector(':myProjectPath1', 'myLib1')
        LibraryComponentSelector defaultBuildComponentSelector2 = new DefaultLibraryComponentSelector(projectPath, libraryName)
        strictlyEquals(defaultBuildComponentSelector1, defaultBuildComponentSelector2) == equality
        (defaultBuildComponentSelector1.hashCode() == defaultBuildComponentSelector2.hashCode()) == hashCode
        (defaultBuildComponentSelector1.toString() == defaultBuildComponentSelector2.toString()) == stringRepresentation

        where:
        projectPath       | libraryName | equality | hashCode | stringRepresentation
        ':myProjectPath1' | 'myLib1'    | true     | true     | true
        ':myProjectPath1' | 'myLib2'    | false    | false    | false
        ':myProjectPath2' | 'myLib1'    | false    | false    | false
    }

    def "prevents matching of null id"() {
        when:
        LibraryComponentSelector defaultBuildComponentSelector = new DefaultLibraryComponentSelector(':myPath', 'myLib')
        defaultBuildComponentSelector.matchesStrictly(null)

        then:
        Throwable t = thrown(AssertionError)
        assert t.message == 'identifier cannot be null'
    }

    def "does not match id for unexpected component selector type"() {
        when:
        LibraryComponentSelector defaultBuildComponentSelector = new DefaultLibraryComponentSelector(':myPath', 'myLib')
        boolean matches = defaultBuildComponentSelector.matchesStrictly(new DefaultModuleComponentIdentifier('group', 'name', '1.0'))

        then:
        assert !matches
    }

    @Unroll
    def "matches id (#projectPath,#libraryName)"() {
        expect:
        LibraryComponentSelector defaultBuildComponentSelector = new DefaultLibraryComponentSelector(':myProjectPath1', 'myLib')
        LibraryComponentIdentifier defaultBuildComponentIdentifier = new DefaultLibraryComponentIdentifier(projectPath, libraryName)
        defaultBuildComponentSelector.matchesStrictly(defaultBuildComponentIdentifier) == matchesId

        where:
        projectPath       | libraryName | matchesId
        ':myProjectPath1' | 'myLib'     | true
        ':myProjectPath2' | 'myLib'     | false
        ':myProjectPath1' | 'myLib2'    | false
    }
}
