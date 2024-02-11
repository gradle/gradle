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

package org.gradle.api.internal.file.copy

import org.gradle.api.Action
import org.gradle.api.InvalidUserDataException
import org.gradle.api.file.FileCopyDetails
import org.gradle.api.file.RelativePath
import org.gradle.api.internal.file.TestFiles
import org.gradle.internal.Actions
import org.gradle.util.TestUtil
import spock.lang.Specification

class CopySpecMatchingTest extends Specification {

    DefaultCopySpec copySpec = new DefaultCopySpec(TestFiles.fileCollectionFactory(), TestUtil.objectFactory(), TestUtil.instantiatorFactory().decorateLenient(), TestFiles.patternSetFactory)

    def canMatchFiles() {
        given:
        FileCopyDetails details1 = details('path/abc.txt')
        FileCopyDetails details2 = details('path/bcd.txt')

        Action matchingAction = Mock()

        when:
        copySpec.filesMatching("**/a*", matchingAction)
        copySpec.copyActions.each { copyAction ->
            copyAction.execute(details1)
            copyAction.execute(details2)
        }

        then:
        1 * matchingAction.execute(details1)
        0 * matchingAction.execute(details2)
    }

    def canMatchFilesWithMultiplePatterns() {
        given:
        FileCopyDetails details1 = details('path/abc.txt')
        FileCopyDetails details2 = details('path/bcd.txt')
        FileCopyDetails details3 = details('path/cde.txt')

        Action matchingAction = Mock()

        when:
        copySpec.filesMatching(["**/a*", "**/c*"], matchingAction)
        copySpec.copyActions.each { copyAction ->
            copyAction.execute(details1)
            copyAction.execute(details2)
            copyAction.execute(details3)
        }

        then:
        1 * matchingAction.execute(details1)
        0 * matchingAction.execute(details2)
        1 * matchingAction.execute(details3)
    }

    def filesMatchingWithNoPatternsThrowsException() {
        given:
        Action matchingAction = Mock()

        when:
        copySpec.filesMatching([], matchingAction)

        then:
        def exception = thrown(InvalidUserDataException)
        exception.message == 'must provide at least one pattern to match'
    }

    def canNotMatchFiles() {
        given:
        FileCopyDetails details1 = details('path/abc.txt')
        FileCopyDetails details2 = details('path/bcd.txt')

        Action matchingAction = Mock()

        when:
        copySpec.filesNotMatching("**/a*", matchingAction)
        copySpec.copyActions.each { copyAction ->
            copyAction.execute(details1)
            copyAction.execute(details2)
        }

        then:
        0 * matchingAction.execute(details1)
        1 * matchingAction.execute(details2)
    }

    def canNotMatchFilesWithMultiplePatterns() {
        given:
        FileCopyDetails details1 = details('path/abc.txt')
        FileCopyDetails details2 = details('path/bcd.txt')
        FileCopyDetails details3 = details('path/cde.txt')

        Action matchingAction = Mock()

        when:
        copySpec.filesNotMatching(["**/a*", "**/c*"], matchingAction)
        copySpec.copyActions.each { copyAction ->
            copyAction.execute(details1)
            copyAction.execute(details2)
            copyAction.execute(details3)
        }

        then:
        0 * matchingAction.execute(details1)
        1 * matchingAction.execute(details2)
        0 * matchingAction.execute(details3)
    }

    def filesNotMatchingWithNoPatternsThrowsException() {
        given:
        Action matchingAction = Mock()

        when:
        copySpec.filesNotMatching([], matchingAction)

        then:
        def exception = thrown(InvalidUserDataException)
        exception.message == 'must provide at least one pattern to not match'
    }

    def matchingSpecInherited() {
        given:
        DefaultCopySpec childSpec = new DefaultCopySpec(TestFiles.fileCollectionFactory(), TestUtil.objectFactory(), TestUtil.instantiatorFactory().decorateLenient(), TestFiles.patternSetFactory)
        CopySpecResolver childResolver = childSpec.buildResolverRelativeToParent(copySpec.buildRootResolver())

        when:
        copySpec.filesMatching("**/*.java", Actions.doNothing())

        then:
        1 == childResolver.allCopyActions.size()
        childResolver.allCopyActions[0] instanceof MatchingCopyAction
    }

    private FileCopyDetails details(String file) {
        FileCopyDetails details = Mock()
        details.relativeSourcePath >> RelativePath.parse(true, file)
        details
    }
}
