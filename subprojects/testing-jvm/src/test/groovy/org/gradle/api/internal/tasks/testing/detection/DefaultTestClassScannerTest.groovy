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
import org.gradle.api.internal.tasks.testing.TestClassProcessor
import org.junit.Test
import spock.lang.Specification

public class DefaultTestClassScannerTest extends Specification {
    private final TestFrameworkDetector detector = Mock()
    private final TestClassProcessor processor = Mock()
    private final FileTree files = Mock()

    @Test
    public void passesEachClassFileToTestClassDetector() {
        DefaultTestClassScanner scanner = new DefaultTestClassScanner(files, detector, processor)

        when:
        scanner.run()

        then:
        1 * detector.startDetection(processor)
        then:
        1 * detector.processTestClass(new File("class1.class"))
        then:
        1 * detector.processTestClass(new File("class2.class"))
        then:
        1 * files.visit(_) >> { args ->
            FileVisitor visitor = args[0]
            assert visitor
            visitor.visitFile({new File('class1.class')} as FileVisitDetails)
            visitor.visitFile({new File('class2.class')} as FileVisitDetails)
        }

        0 * _._
    }
}
