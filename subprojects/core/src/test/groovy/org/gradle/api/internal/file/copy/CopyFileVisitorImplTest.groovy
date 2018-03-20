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
import org.gradle.api.file.FileTree
import org.gradle.api.file.FileVisitDetails
import org.gradle.api.file.FileVisitor
import org.gradle.api.internal.file.CopyActionProcessingStreamAction
import org.gradle.internal.nativeintegration.filesystem.FileSystem
import org.gradle.internal.reflect.Instantiator
import spock.lang.Specification

class CopyFileVisitorImplTest extends Specification {
    def resolvedSpec = Mock(ResolvedCopySpec)
    def action = Mock(CopyActionProcessingStreamAction)
    def instantiator = Mock(Instantiator)
    def fileSystem = Mock(FileSystem)
    def source = Mock(FileTree)
    FileVisitor copyFileVisitorImpl

    def setup() {
        copyFileVisitorImpl = new CopyFileVisitorImpl(resolvedSpec, action, instantiator, fileSystem, false)
    }

    def "visit directory"() {
        given:
        def dirDetails = Mock(FileVisitDetails)
        DefaultFileCopyDetails defaultFileCopyDetails = new DefaultFileCopyDetails(dirDetails, resolvedSpec, fileSystem)

        when:
        copyFileVisitorImpl.visitDir(dirDetails)

        then:
        1 * instantiator.newInstance(DefaultFileCopyDetails.class, dirDetails, resolvedSpec, fileSystem) >> defaultFileCopyDetails
        1 * action.processFile(defaultFileCopyDetails)
        0 * defaultFileCopyDetails.excluded
    }

    def "visit file if no action are defined in copy spec"() {
        given:
        def dirDetails = Mock(FileVisitDetails)
        DefaultFileCopyDetails defaultFileCopyDetails = new DefaultFileCopyDetails(dirDetails, resolvedSpec, fileSystem)

        when:
        copyFileVisitorImpl.visitFile(dirDetails)

        then:
        1 * instantiator.newInstance(DefaultFileCopyDetails.class, dirDetails, resolvedSpec, fileSystem) >> defaultFileCopyDetails
        1 * resolvedSpec.getCopyActions() >> []
        1 * action.processFile(defaultFileCopyDetails)
        0 * defaultFileCopyDetails.excluded
    }

    def "visit file for actions defined in copy spec"() {
        given:
        def dirDetails = Mock(FileVisitDetails)
        def defaultFileCopyDetails = Mock(DefaultFileCopyDetails)
        def fileCopyAction1 = Mock(Action)
        def fileCopyAction2 = Mock(Action)

        when:
        copyFileVisitorImpl.visitFile(dirDetails)

        then:
        1 * instantiator.newInstance(DefaultFileCopyDetails.class, dirDetails, resolvedSpec, fileSystem) >> defaultFileCopyDetails
        1 * resolvedSpec.getCopyActions() >> [fileCopyAction1, fileCopyAction2]
        1 * action.processFile(defaultFileCopyDetails)
        1 * fileCopyAction1.execute(defaultFileCopyDetails)
        1 * fileCopyAction2.execute(defaultFileCopyDetails)
        2 * defaultFileCopyDetails.isExcluded() >> false
    }

    def "visit file for excluded file copy details"() {
        given:
        def dirDetails = Mock(FileVisitDetails)
        def defaultFileCopyDetails = Mock(DefaultFileCopyDetails)
        def fileCopyAction1 = Mock(Action)
        def fileCopyAction2 = Mock(Action)

        when:
        copyFileVisitorImpl.visitFile(dirDetails)

        then:
        1 * instantiator.newInstance(DefaultFileCopyDetails.class, dirDetails, resolvedSpec, fileSystem) >> defaultFileCopyDetails
        1 * resolvedSpec.getCopyActions() >> [fileCopyAction1, fileCopyAction2]
        0 * action.processFile(defaultFileCopyDetails)
        1 * fileCopyAction1.execute(defaultFileCopyDetails)
        1 * defaultFileCopyDetails.excluded >> true
        0 * fileCopyAction2.execute(defaultFileCopyDetails)
    }
}
