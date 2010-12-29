/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.tasks.scala;

import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.AbstractTask;
import org.gradle.api.tasks.AbstractTaskTest;
import org.gradle.util.GFileUtils;
import org.gradle.util.JUnit4GroovyMockery;
import org.gradle.util.WrapUtil;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

import static org.gradle.api.tasks.compile.AbstractCompileTest.*;
import static org.gradle.util.Matchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

@RunWith(JMock.class)
public class ScalaDocTest extends AbstractTaskTest {
    private ScalaDoc scalaDoc;
    private AntScalaDoc antScalaDocMock;
    private JUnit4Mockery context = new JUnit4GroovyMockery();
    private File destDir;
    private File srcDir;

    @Override
    public AbstractTask getTask() {
        return scalaDoc;
    }

    @Override
    @Before
    public void setUp() {
        super.setUp();
        destDir = getProject().file("destDir");
        srcDir = getProject().file("src");
        GFileUtils.touch(new File(srcDir, "file.scala"));
        scalaDoc = createTask(ScalaDoc.class);
        antScalaDocMock = context.mock(AntScalaDoc.class);
        scalaDoc.setAntScalaDoc(antScalaDocMock);
    }

    @Test
    public void testExecutesAntScalaDoc() {
        setUpMocksAndAttributes(scalaDoc);
        scalaDoc.generate();
    }

    @Test
    public void testScalaIncludes() {
        assertSame(scalaDoc.include(TEST_PATTERN_1, TEST_PATTERN_2), scalaDoc);
        assertEquals(scalaDoc.getIncludes(), WrapUtil.toLinkedSet(TEST_PATTERN_1, TEST_PATTERN_2));

        assertSame(scalaDoc.include(TEST_PATTERN_3), scalaDoc);
        assertEquals(scalaDoc.getIncludes(), WrapUtil.toLinkedSet(TEST_PATTERN_1, TEST_PATTERN_2, TEST_PATTERN_3));
    }

    @Test
    public void testScalaExcludes() {
        assertSame(scalaDoc.exclude(TEST_PATTERN_1, TEST_PATTERN_2), scalaDoc);
        assertEquals(scalaDoc.getExcludes(), WrapUtil.toLinkedSet(TEST_PATTERN_1, TEST_PATTERN_2));

        assertSame(scalaDoc.exclude(TEST_PATTERN_3), scalaDoc);
        assertEquals(scalaDoc.getExcludes(), WrapUtil.toLinkedSet(TEST_PATTERN_1, TEST_PATTERN_2, TEST_PATTERN_3));
    }

    @Test
    public void testSetsDocTitleIfNotSet() {
        setUpMocksAndAttributes(scalaDoc);
        scalaDoc.setTitle("title");

        scalaDoc.generate();

        assertThat(scalaDoc.getScalaDocOptions().getDocTitle(), equalTo("title"));
    }

    private void setUpMocksAndAttributes(final ScalaDoc docTask) {
        docTask.source(srcDir);
        docTask.setDestinationDir(destDir);
        docTask.setScalaClasspath(context.mock(FileCollection.class));
        docTask.setClasspath(context.mock(FileCollection.class));

        context.checking(new Expectations() {{
            one(antScalaDocMock).execute(
                    with(hasSameItems(scalaDoc.getSource())),
                    with(equalTo(scalaDoc.getDestinationDir())),
                    with(equalTo(scalaDoc.getClasspath())),
                    with(equalTo(scalaDoc.getScalaClasspath())),
                    with(sameInstance(scalaDoc.getScalaDocOptions())));
        }});
    }
}
