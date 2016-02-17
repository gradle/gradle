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

import org.gradle.util.JUnit4GroovyMockery
import org.jmock.integration.junit4.JMock
import org.junit.runner.RunWith
import org.junit.Test

import org.gradle.api.internal.tasks.testing.TestClassProcessor
import org.jmock.Sequence
import org.gradle.api.file.FileTree
import static org.hamcrest.Matchers.*
import org.gradle.api.file.FileVisitDetails

@RunWith(JMock.class)
public class DefaultTestClassScannerTest {
    private final JUnit4GroovyMockery context = new JUnit4GroovyMockery()
    private final TestFrameworkDetector detector = context.mock(TestFrameworkDetector.class)
    private final TestClassProcessor processor = context.mock(TestClassProcessor.class)
    private final FileTree files = context.mock(FileTree.class)

    @Test
    public void passesEachClassFileToTestClassDetector() {
        DefaultTestClassScanner scanner = new DefaultTestClassScanner(files, detector, processor)

        context.checking {
            Sequence sequence = context.sequence('seq')
            one(files).visit(withParam(notNullValue()))
            will { visitor ->
                visitor.visitFile({new File('class1.class')} as FileVisitDetails)
                visitor.visitFile({new File('class2.class')} as FileVisitDetails)
            }
            one(detector).startDetection(processor)
            inSequence(sequence)
            one(detector).processTestClass(new File('class1.class'))
            one(detector).processTestClass(new File('class2.class'))
            inSequence(sequence)
        }
        
        scanner.run()
    }
}
