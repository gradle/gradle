/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.file.collections

import org.gradle.api.GradleException
import org.gradle.api.file.FileVisitDetails
import org.gradle.api.file.FileVisitor
import org.gradle.api.file.ReproducibleFileVisitor
import spock.lang.Specification
import spock.lang.Subject

@Subject(FailOnBrokenSymbolicLinkVisitor)
class FailOnBrokenSymbolicLinkVisitorTest extends Specification {
    def detailFile = new File("some/file/with/details")
    def delegate = Mock(FileVisitor)
    def fileVisitDetails = Mock(FileVisitDetails) {
        getFile() >> detailFile
    }
    def visitor = new FailOnBrokenSymbolicLinkVisitor(delegate)

    def "throws GradleException when visiting broken symbolic link"() {
        when:
        visitor.visitBrokenSymbolicLink(fileVisitDetails)

        then:
        def ex = thrown(GradleException)
        ex.message == "Could not list contents of '${detailFile.path}'. Couldn't follow symbolic link."
    }

    def "delegates visitFile to FileVisitor#visitFile"() {
        when:
        visitor.visitFile(fileVisitDetails)

        then:
        1 * delegate.visitFile(fileVisitDetails)
        0 * _
    }

    def "delegates visitDirectory to FileVisitor#visitDir"() {
        when:
        visitor.visitDirectory(fileVisitDetails)

        then:
        1 * delegate.visitDir(fileVisitDetails)
        0 * _
    }

    def "can get the FileVisitor delegate"() {
        expect:
        visitor.delegate == delegate
    }

    def "can return correct reproducible file order value"() {
        expect:
        visitor.reproducibleOrder == false

        new FailOnBrokenSymbolicLinkVisitor(newReproducibleFileVisitor(false)).reproducibleOrder == false

        new FailOnBrokenSymbolicLinkVisitor(newReproducibleFileVisitor(true)).reproducibleOrder == true
    }

    ReproducibleFileVisitor newReproducibleFileVisitor(boolean reproducible) {
        return Mock(ReproducibleFileVisitor) {
            isReproducibleFileOrder() >> reproducible
        }
    }
}
