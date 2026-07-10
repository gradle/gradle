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
import spock.lang.Specification

import static org.gradle.util.Matchers.strictlyEquals

class DefaultLibraryBinaryIdentifierTest extends Specification {
    def "is instantiated with non-null constructor parameter values"() {
        when:
        LibraryBinaryIdentifier defaultBuildComponentIdentifier = new DefaultLibraryBinaryIdentifier(':myPath', 'myLib', 'api')

        then:
        defaultBuildComponentIdentifier.projectPath == ':myPath'
        defaultBuildComponentIdentifier.libraryName == 'myLib'
        defaultBuildComponentIdentifier.variant == 'api'
        defaultBuildComponentIdentifier.displayName == /project ':myPath' library 'myLib' variant 'api'/
        defaultBuildComponentIdentifier.toString() == /project ':myPath' library 'myLib' variant 'api'/
    }

    def "is instantiated with null project constructor parameter value"() {
        when:
        new DefaultLibraryBinaryIdentifier(null, 'foo', 'api')

        then:
        Throwable t = thrown(AssertionError)
        t.message == 'project path cannot be null'
    }

    def "is instantiated with null library constructor parameter value"() {
        when:
        new DefaultLibraryBinaryIdentifier('foo', null, 'api')

        then:
        Throwable t = thrown(AssertionError)
        t.message == 'library name cannot be null'
    }

    def "is instantiated with null variant constructor parameter value"() {
        when:
        new DefaultLibraryBinaryIdentifier('foo', 'bar', null)

        then:
        Throwable t = thrown(AssertionError)
        t.message == 'variant cannot be null'
    }

    def "can compare with other instance (#projectPath,#libraryName,#variant)"() {
        expect:
        LibraryBinaryIdentifier defaultBuildComponentIdentifier1 = new DefaultLibraryBinaryIdentifier(':myProjectPath1', 'myLib', 'api')
        LibraryBinaryIdentifier defaultBuildComponentIdentifier2 = new DefaultLibraryBinaryIdentifier(projectPath, libraryName, variant)
        strictlyEquals(defaultBuildComponentIdentifier1, defaultBuildComponentIdentifier2) == equality
        (defaultBuildComponentIdentifier1.hashCode() == defaultBuildComponentIdentifier2.hashCode()) == hashCode
        (defaultBuildComponentIdentifier1.toString() == defaultBuildComponentIdentifier2.toString()) == stringRepresentation

        where:
        projectPath       | libraryName | variant | equality | hashCode | stringRepresentation
        ':myProjectPath1' | 'myLib'     | 'api'   | true     | true     | true
        ':myProjectPath2' | 'myLib'     | 'api'   | false    | false    | false
        ':myProjectPath1' | 'myLib2'    | 'api'   | false    | false    | false
        ':myProjectPath1' | 'myLib'     | 'impl'  | false    | false    | false
    }
}
