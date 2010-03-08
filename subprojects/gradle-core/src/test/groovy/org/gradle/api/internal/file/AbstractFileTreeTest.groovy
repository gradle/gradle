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
package org.gradle.api.internal.file


import static org.junit.Assert.*
import static org.hamcrest.Matchers.*
import static org.gradle.util.Matchers.*
import org.gradle.util.JUnit4GroovyMockery
import org.jmock.integration.junit4.JMock
import org.junit.runner.RunWith
import org.junit.Test
import org.gradle.api.file.FileTree
import org.gradle.api.file.FileVisitor
import org.gradle.api.file.FileVisitDetails

@RunWith(JMock.class)
public class AbstractFileTreeTest {
    private final JUnit4GroovyMockery context = new JUnit4GroovyMockery()

    @Test
    public void isEmptyWhenVisitsNoFiles() {
        def tree = new TestFileTree([])
        assertTrue(tree.empty)
    }

    @Test
    public void isNotEmptyWhenVisitsFirstFile() {
        FileVisitDetails file = context.mock(FileVisitDetails.class)
        def tree = new TestFileTree([file])

        context.checking {
            one(file).stopVisiting()
        }

        assertFalse(tree.empty)
    }
}

class TestFileTree extends AbstractFileTree {
    List contents

    def TestFileTree(List files) {
        this.contents = files
    }

    String getDisplayName() {
        throw new UnsupportedOperationException();
    }

    FileTree visit(FileVisitor visitor) {
        contents.each {FileVisitDetails details ->
            visitor.visitFile(details)
        }
        this
    }
}

