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

import org.gradle.api.file.FileCopyDetails
import org.gradle.api.file.FileTree
import org.gradle.api.file.RelativePath
import org.gradle.api.internal.file.TestFiles
import org.gradle.internal.reflect.DirectInstantiator
import spock.lang.Specification

/**
 * Tests for CopySpec.matching and notMatching facilities
 * @author Kyle Mahan
 */
class CopySpecMatchingTest extends Specification {

    CopySpecImpl copySpec = new CopySpecImpl(TestFiles.resolver(), new DirectInstantiator(), null)

    FileTree fileTree = Mock()

    def canMatchFiles() {
        given:

        FileCopyDetails details1 = Mock()
        FileCopyDetails details2 = Mock()

        details1.relativePath >>  RelativePath.parse(true, 'path/abc.txt')
        details2.relativePath >> RelativePath.parse(true, 'path/bcd.txt')

        Closure matchingClosure = Mock()
        matchingClosure.clone() >> matchingClosure

        when:
        copySpec.filesMatching("**/a*", matchingClosure)
        copySpec.allCopyActions.each { copyAction ->
            copyAction.execute(details1)
            copyAction.execute(details2)
        }

        then:
        1 * matchingClosure.setDelegate(details1)
        1 * matchingClosure.call()
    }


    def canNotMatchFiles() {
        given:

        FileCopyDetails details1 = Mock()
        FileCopyDetails details2 = Mock()

        details1.relativePath >>  RelativePath.parse(true, 'path/abc.txt')
        details2.relativePath >> RelativePath.parse(true, 'path/bcd.txt')

        Closure matchingClosure = Mock()
        matchingClosure.clone() >> matchingClosure

        when:
        copySpec.filesNotMatching("**/a*", matchingClosure)
        copySpec.allCopyActions.each { copyAction ->
            copyAction.execute(details1)
            copyAction.execute(details2)
        }

        then:
        1 * matchingClosure.setDelegate(details2)
        1 * matchingClosure.call()
    }

    def matchingSpecInherited() {
        given:
        CopySpecImpl childSpec = copySpec.addChild()
        when:
        copySpec.filesMatching("**/*.java") {}
        then:
        1 == childSpec.allCopyActions.size()
        childSpec.allCopyActions[0] instanceof MatchingCopyAction
    }
}
