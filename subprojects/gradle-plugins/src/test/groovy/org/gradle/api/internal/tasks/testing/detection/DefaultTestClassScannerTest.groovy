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
import org.junit.Rule
import org.gradle.util.TemporaryFolder

import org.gradle.api.internal.tasks.testing.TestClassProcessor
import org.jmock.Sequence

@RunWith(JMock.class)
public class DefaultTestClassScannerTest {
    private final JUnit4GroovyMockery context = new JUnit4GroovyMockery()
    private final TestFrameworkDetector detector = context.mock(TestFrameworkDetector.class)
    private final TestClassProcessor processor = context.mock(TestClassProcessor.class)
    @Rule
    public final TemporaryFolder tmpDir = new TemporaryFolder()

    @Test
    public void passesEachClassFileToTestClassDetector() {
        DefaultTestClassScanner scanner = new DefaultTestClassScanner(tmpDir.dir, ['**/Class*'] as Set, ['**/excluded*'] as Set, detector, processor)

        tmpDir.dir.create {
            dir1 {
                file 'Class1.class'
                file 'Class1.xml'
            }
            dir2 {
                file 'Class2.class'
                file 'excluded.class'
            }
        }

        context.checking {
            Sequence sequence = context.sequence('seq')
            one(detector).startDetection(processor)
            inSequence(sequence)
            one(detector).processTestClass(tmpDir.file('dir1/Class1.class'))
            one(detector).processTestClass(tmpDir.file('dir2/Class2.class'))
            inSequence(sequence)
        }
        
        scanner.run()
    }
}
