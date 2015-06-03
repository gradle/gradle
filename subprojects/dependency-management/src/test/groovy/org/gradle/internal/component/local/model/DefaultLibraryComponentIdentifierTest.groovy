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
import spock.lang.Specification
import spock.lang.Unroll

import static org.gradle.util.Matchers.strictlyEquals

class DefaultLibraryComponentIdentifierTest extends Specification {
    def "is instantiated with non-null constructor parameter values"() {
        when:
        LibraryComponentIdentifier defaultBuildComponentIdentifier = new DefaultLibraryComponentIdentifier(':myPath', 'myLib')

        then:
        defaultBuildComponentIdentifier.projectPath == ':myPath'
        defaultBuildComponentIdentifier.libraryName == 'myLib'
        defaultBuildComponentIdentifier.displayName == /project ':myPath' library 'myLib'/
        defaultBuildComponentIdentifier.toString() == /project ':myPath' library 'myLib'/
    }

    def "is instantiated with null project constructor parameter value"() {
        when:
        new DefaultLibraryComponentIdentifier(null, 'foo')

        then:
        Throwable t = thrown(AssertionError)
        t.message == 'project path cannot be null'
    }

    def "is instantiated with null library constructor parameter value"() {
        when:
        new DefaultLibraryComponentIdentifier('foo', null)

        then:
        Throwable t = thrown(AssertionError)
        t.message == 'library name cannot be null'
    }

    @Unroll
    def "can compare with other instance (#projectPath,#libraryName)"() {
        expect:
        LibraryComponentIdentifier defaultBuildComponentIdentifier1 = new DefaultLibraryComponentIdentifier(':myProjectPath1', 'myLib')
        LibraryComponentIdentifier defaultBuildComponentIdentifier2 = new DefaultLibraryComponentIdentifier(projectPath, libraryName)
        strictlyEquals(defaultBuildComponentIdentifier1, defaultBuildComponentIdentifier2) == equality
        (defaultBuildComponentIdentifier1.hashCode() == defaultBuildComponentIdentifier2.hashCode()) == hashCode
        (defaultBuildComponentIdentifier1.toString() == defaultBuildComponentIdentifier2.toString()) == stringRepresentation

        where:
        projectPath       | libraryName | equality | hashCode | stringRepresentation
        ':myProjectPath1' | 'myLib'     | true     | true     | true
        ':myProjectPath2' | 'myLib'     | false    | false    | false
        ':myProjectPath1' | 'myLib2'    | false    | false    | false
    }
}
