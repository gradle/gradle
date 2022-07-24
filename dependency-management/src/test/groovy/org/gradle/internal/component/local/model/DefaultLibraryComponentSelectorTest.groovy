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

import org.gradle.api.artifacts.component.LibraryBinaryIdentifier
import org.gradle.api.artifacts.component.LibraryComponentSelector
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import spock.lang.Specification

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

    def "can be instantiated with null constructor library name value"() {
        when:
        LibraryComponentSelector defaultBuildComponentSelector = new DefaultLibraryComponentSelector(':myPath', null)

        then:
        defaultBuildComponentSelector.projectPath == ':myPath'
        defaultBuildComponentSelector.libraryName == null
        defaultBuildComponentSelector.displayName == /project ':myPath'/
        defaultBuildComponentSelector.toString() == /project ':myPath'/
    }

    def "can be instantiated with null constructor variant value"() {
        when:
        LibraryComponentSelector defaultBuildComponentSelector = new DefaultLibraryComponentSelector(':myPath', 'myLib')

        then:
        defaultBuildComponentSelector.projectPath == ':myPath'
        defaultBuildComponentSelector.libraryName == 'myLib'
        defaultBuildComponentSelector.displayName == /project ':myPath' library 'myLib'/
        defaultBuildComponentSelector.toString() == /project ':myPath' library 'myLib'/
    }

    def "cannot be instantiated with null project constructor parameter value"() {
        when:
        new DefaultLibraryComponentSelector(null, 'myLib')

        then:
        Throwable t = thrown(AssertionError)
        t.message == 'project path cannot be null or empty'
    }

    def "cannot be instantiated with empty project constructor parameter value"() {
        when:
        new DefaultLibraryComponentSelector('', 'myLib')

        then:
        Throwable t = thrown(AssertionError)
        t.message == 'project path cannot be null or empty'
    }

    def "can compare (#projectPath1,#libraryName1) with other instance (#projectPath2,#libraryName2)"() {
        expect:
        LibraryComponentSelector defaultBuildComponentSelector1 = new DefaultLibraryComponentSelector(projectPath1, libraryName1)
        LibraryComponentSelector defaultBuildComponentSelector2 = new DefaultLibraryComponentSelector(projectPath2, libraryName2)
        strictlyEquals(defaultBuildComponentSelector1, defaultBuildComponentSelector2) == equality
        (defaultBuildComponentSelector1.hashCode() == defaultBuildComponentSelector2.hashCode()) == hashCode
        (defaultBuildComponentSelector1.toString() == defaultBuildComponentSelector2.toString()) == stringRepresentation

        where:
        projectPath1      | libraryName1 | projectPath2      | libraryName2 | equality | hashCode | stringRepresentation
        ':myProjectPath1' | 'myLib1'     | ':myProjectPath1' | 'myLib1'     | true     | true     | true
        ':myProjectPath1' | 'myLib1'     | ':myProjectPath1' | 'myLib2'     | false    | false    | false
        ':myProjectPath1' | 'myLib1'     | ':myProjectPath2' | 'myLib1'     | false    | false    | false
        ':myProjectPath1' | 'myLib1'     | ':myProjectPath2' | null         | false    | false    | false
        ':myProjectPath1' | null         | ':myProjectPath1' | 'myLib1'     | false    | false    | false
        ':myProjectPath1' | null         | ':myProjectPath1' | 'myLib2'     | false    | false    | false
        ':myProjectPath1' | null         | ':myProjectPath2' | 'myLib1'     | false    | false    | false
        ':myProjectPath1' | null         | ':myProjectPath2' | null         | false    | false    | false
        ':myProjectPath1' | null         | ':myProjectPath1' | null         | true     | true     | true
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
        boolean matches = defaultBuildComponentSelector.matchesStrictly(new DefaultModuleComponentIdentifier(DefaultModuleIdentifier.newId('group', 'name'), '1.0'))

        then:
        assert !matches
    }

    def "matches id (#projectPath1,#libraryName1, #variant1) with (#projectPath2,#libraryName2, #variant2)"() {
        expect:
        LibraryComponentSelector defaultBuildComponentSelector = new DefaultLibraryComponentSelector(projectPath1, libraryName1, variant1)
        LibraryBinaryIdentifier defaultBuildComponentIdentifier = new DefaultLibraryBinaryIdentifier(projectPath2, libraryName2, variant2)
        defaultBuildComponentSelector.matchesStrictly(defaultBuildComponentIdentifier) == matchesId

        where:
        projectPath1      | libraryName1 | projectPath2      | libraryName2 | variant1 | variant2 | matchesId
        ':myProjectPath1' | 'myLib1'     | ':myProjectPath1' | 'myLib1'     | 'api'    | 'api'    | true
        ':myProjectPath1' | 'myLib1'     | ':myProjectPath1' | 'myLib1'     | 'api'    | 'impl'   | false
        ':myProjectPath1' | 'myLib1'     | ':myProjectPath2' | 'myLib1'     | 'impl'   | 'impl'   | false
        ':myProjectPath1' | 'myLib1'     | ':myProjectPath2' | 'myLib1'     | 'api'    | 'api'    | false
        ':myProjectPath1' | 'myLib1'     | ':myProjectPath1' | 'myLib2'     | 'api'    | 'api'    | false
        ':myProjectPath1' | null         | ':myProjectPath1' | 'foo'        | 'api'    | 'api'    | false
        ':myProjectPath1' | null         | ':myProjectPath2' | 'foo'        | 'api'    | 'api'    | false
        ':myProjectPath1' | null         | ':myProjectPath1' | 'foo'        | 'api'    | 'api'    | false
        ':myProjectPath1' | 'myLib1'     | ':myProjectPath2' | 'myLib1'     | 'api'    | 'api'    | false
        ':myProjectPath1' | 'myLib1'     | ':myProjectPath2' | 'myLib1'     | 'api'    | 'api'    | false
        ':myProjectPath1' | 'myLib1'     | ':myProjectPath1' | 'myLib2'     | 'api'    | 'api'    | false
    }
}
