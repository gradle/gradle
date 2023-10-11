/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.detection

import org.gradle.api.file.FileTree
import org.gradle.api.file.FileVisitDetails
import org.gradle.api.file.FileVisitor
import org.gradle.api.file.RelativePath
import org.gradle.api.internal.file.DefaultFileVisitDetails
import org.gradle.api.internal.tasks.testing.TestClassProcessor
import spock.lang.Specification
import spock.lang.Subject

class DefaultTestClassScannerTest extends Specification {
    def files = Mock(FileTree)
    def detector = Mock(TestFrameworkDetector)
    def processor = Stub(TestClassProcessor)

    @Subject
    def scanner = new DefaultTestClassScanner(files, detector, processor)

    void passesEachClassFileToTestClassDetector() {
        given:
        def class1 = stubFileVisitDetails('class1')
        def class2 = stubFileVisitDetails('class2')

        when:
        scanner.run()

        then:
        1 * detector.startDetection(processor)
        then:
        1 * files.visit(_) >> { args ->
            FileVisitor visitor = args[0]
            assert visitor
            visitor.visitFile(class1)
            visitor.visitFile(class2)
        }
        then:
        1 * detector.processTestClass({ it.file.is(class1.file) && it.relativePath.is(class1.relativePath) })
        then:
        1 * detector.processTestClass({ it.file.is(class2.file) && it.relativePath.is(class2.relativePath) })

        0 * _._
    }

    void skipAnonymousClass() {
        when:
        scanner.run()

        then:
        1 * detector.startDetection(processor)
        then:
        1 * files.visit(_) >> { args ->
            FileVisitor visitor = args[0]
            assert visitor
            visitor.visitFile(stubFileVisitDetails('AnonymousClass$1'))
            visitor.visitFile(stubFileVisitDetails('AnonymousClass$1$22'))
        }

        0 * _._
    }

    FileVisitDetails stubFileVisitDetails(String className) {
        return new DefaultFileVisitDetails(new File("${className}.class"), new RelativePath(false, "${className}.class"), null, null, null)
    }
}
