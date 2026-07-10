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
import org.gradle.api.file.FileVisitDetails
import org.gradle.api.file.FileVisitor
import org.gradle.api.internal.file.CopyActionProcessingStreamAction
import org.gradle.api.internal.provider.PropertyFactory
import org.gradle.internal.nativeintegration.filesystem.FileSystem
import org.gradle.internal.reflect.Instantiator
import spock.lang.Specification

class CopyFileVisitorImplTest extends Specification {
    CopySpecResolver specResolver = Mock()
    CopyActionProcessingStreamAction action = Mock()
    Instantiator instantiator = Mock()
    PropertyFactory propertyFactory = Mock()
    FileSystem fileSystem = Mock()
    FileTree source = Mock()
    FileVisitor copyFileVisitorImpl

    def setup() {
        copyFileVisitorImpl = new CopyFileVisitorImpl(specResolver, action, instantiator, propertyFactory, fileSystem, false)
    }

    def "visit directory"() {
        given:
        FileVisitDetails dirDetails = Mock()
        DefaultFileCopyDetails defaultFileCopyDetails = new DefaultFileCopyDetails(dirDetails, specResolver, instantiator, propertyFactory, fileSystem)

        when:
        copyFileVisitorImpl.visitDir(dirDetails)

        then:
        1 * instantiator.newInstance(DefaultFileCopyDetails.class, dirDetails, specResolver, instantiator, propertyFactory, fileSystem) >> defaultFileCopyDetails
        1 * action.processFile(defaultFileCopyDetails)
        0 * defaultFileCopyDetails.excluded
    }

    def "visit file if no action are defined in copy spec"() {
        given:
        FileVisitDetails dirDetails = Mock()
        DefaultFileCopyDetails defaultFileCopyDetails = new DefaultFileCopyDetails(dirDetails, specResolver, instantiator, propertyFactory, fileSystem)

        when:
        copyFileVisitorImpl.visitFile(dirDetails)

        then:
        1 * instantiator.newInstance(DefaultFileCopyDetails.class, dirDetails, specResolver, instantiator, propertyFactory, fileSystem) >> defaultFileCopyDetails
        1 * specResolver.getAllCopyActions() >> []
        1 * action.processFile(defaultFileCopyDetails)
        0 * defaultFileCopyDetails.excluded
    }

    def "visit file for actions defined in copy spec"() {
        given:
        FileVisitDetails dirDetails = Mock()
        DefaultFileCopyDetails defaultFileCopyDetails = Mock()
        Action<? super FileCopyDetails> fileCopyAction1 = Mock()
        Action<? super FileCopyDetails> fileCopyAction2 = Mock()

        when:
        copyFileVisitorImpl.visitFile(dirDetails)

        then:
        1 * instantiator.newInstance(DefaultFileCopyDetails.class, dirDetails, specResolver, instantiator, propertyFactory, fileSystem) >> defaultFileCopyDetails
        1 * specResolver.getAllCopyActions() >> [fileCopyAction1, fileCopyAction2]
        1 * action.processFile(defaultFileCopyDetails)
        1 * fileCopyAction1.execute(defaultFileCopyDetails)
        1 * fileCopyAction2.execute(defaultFileCopyDetails)
        2 * defaultFileCopyDetails.isExcluded() >> false
    }

    def "visit file for excluded file copy details"() {
        given:
        FileVisitDetails dirDetails = Mock()
        DefaultFileCopyDetails defaultFileCopyDetails = Mock()
        Action<? super FileCopyDetails> fileCopyAction1 = Mock()
        Action<? super FileCopyDetails> fileCopyAction2 = Mock()

        when:
        copyFileVisitorImpl.visitFile(dirDetails)

        then:
        1 * instantiator.newInstance(DefaultFileCopyDetails.class, dirDetails, specResolver, instantiator, propertyFactory, fileSystem) >> defaultFileCopyDetails
        1 * specResolver.getAllCopyActions() >> [fileCopyAction1, fileCopyAction2]
        0 * action.processFile(defaultFileCopyDetails)
        1 * fileCopyAction1.execute(defaultFileCopyDetails)
        1 * defaultFileCopyDetails.excluded >> true
        0 * fileCopyAction2.execute(defaultFileCopyDetails)
    }
}
