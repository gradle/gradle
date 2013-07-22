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
import org.gradle.api.file.FileCopyDetails
import org.gradle.api.file.FileTree
import org.gradle.api.file.RelativePath
import org.gradle.api.internal.Actions
import org.gradle.api.internal.file.TestFiles
import org.gradle.internal.reflect.DirectInstantiator
import spock.lang.Specification

class CopySpecMatchingTest extends Specification {

    DefaultCopySpec copySpec = new DefaultCopySpec(TestFiles.resolver(), new DirectInstantiator(), null)

    FileTree fileTree = Mock()

    def canMatchFiles() {
        given:

        FileCopyDetails details1 = Mock()
        FileCopyDetails details2 = Mock()

        details1.relativePath >>  RelativePath.parse(true, 'path/abc.txt')
        details2.relativePath >> RelativePath.parse(true, 'path/bcd.txt')

        Action matchingAction = Mock()

        when:
        copySpec.filesMatching("**/a*", matchingAction)
        copySpec.allCopyActions.each { copyAction ->
            copyAction.execute(details1)
            copyAction.execute(details2)
        }

        then:
        1 * matchingAction.execute(details1)
    }


    def canNotMatchFiles() {
        given:

        FileCopyDetails details1 = Mock()
        FileCopyDetails details2 = Mock()

        details1.relativePath >>  RelativePath.parse(true, 'path/abc.txt')
        details2.relativePath >> RelativePath.parse(true, 'path/bcd.txt')

        Action matchingAction = Mock()

        when:
        copySpec.filesNotMatching("**/a*", matchingAction)
        copySpec.allCopyActions.each { copyAction ->
            copyAction.execute(details1)
            copyAction.execute(details2)
        }

        then:
        1 * matchingAction.execute(details2)
    }

    def matchingSpecInherited() {
        given:
        DefaultCopySpec childSpec = copySpec.addChild()
        when:
        copySpec.filesMatching("**/*.java", Actions.doNothing())
        then:
        1 == childSpec.allCopyActions.size()
        childSpec.allCopyActions[0] instanceof MatchingCopyAction
    }
}
