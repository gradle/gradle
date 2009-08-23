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

import org.gradle.api.internal.AbstractTask;
import org.gradle.api.internal.file.AbstractFileCollection;
import org.gradle.api.tasks.AbstractTaskTest;
import static org.gradle.api.tasks.compile.AbstractCompileTest.*;
import org.gradle.api.tasks.util.ExistingDirsFilter;
import org.gradle.util.WrapUtil;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ScalaDocTest extends AbstractTaskTest {
    private ScalaDoc scalaDoc;
    private AntScalaDoc antScalaDocMock;
    private JUnit4Mockery context = new JUnit4Mockery();

    @Override
    public AbstractTask getTask() {
        return scalaDoc;
    }

    @Override
    @Before
    public void setUp() {
        super.setUp();
        context.setImposteriser(ClassImposteriser.INSTANCE);
        scalaDoc = createTask(ScalaDoc.class);
        antScalaDocMock = context.mock(AntScalaDoc.class);
        scalaDoc.setAntScalaDoc(antScalaDocMock);
    }

    @Test
    public void testExecutesAntScalaDoc() {
        setUpMocksAndAttributes(scalaDoc);
        context.checking(new Expectations() {{
            one(antScalaDocMock).execute(scalaDoc.getScalaSrcDirs(), scalaDoc.getScalaIncludes(),
                    scalaDoc.getScalaExcludes(), scalaDoc.getDestinationDir(), scalaDoc.getClasspath(),
                    scalaDoc.getScalaDocOptions());
        }});
        scalaDoc.execute();
    }

    @Test
    public void testScalaIncludes() {
        assertSame(scalaDoc.scalaInclude(TEST_PATTERN_1, TEST_PATTERN_2), scalaDoc);
        assertEquals(scalaDoc.getScalaIncludes(), WrapUtil.toLinkedSet(TEST_PATTERN_1, TEST_PATTERN_2));

        assertSame(scalaDoc.scalaInclude(TEST_PATTERN_3), scalaDoc);
        assertEquals(scalaDoc.getScalaIncludes(), WrapUtil.toLinkedSet(TEST_PATTERN_1, TEST_PATTERN_2, TEST_PATTERN_3));
    }

    @Test
    public void testScalaExcludes() {
        assertSame(scalaDoc.scalaExclude(TEST_PATTERN_1, TEST_PATTERN_2), scalaDoc);
        assertEquals(scalaDoc.getScalaExcludes(), WrapUtil.toLinkedSet(TEST_PATTERN_1, TEST_PATTERN_2));

        assertSame(scalaDoc.scalaExclude(TEST_PATTERN_3), scalaDoc);
        assertEquals(scalaDoc.getScalaExcludes(), WrapUtil.toLinkedSet(TEST_PATTERN_1, TEST_PATTERN_2, TEST_PATTERN_3));
    }

    private void setUpMocksAndAttributes(final ScalaDoc docTask) {
        docTask.setScalaSrcDirs(WrapUtil.toList(new File("sourceDir1"), new File("sourceDir2")));
        docTask.setScalaIncludes(TEST_INCLUDES);
        docTask.setScalaExcludes(TEST_EXCLUDES);
        docTask.existentDirsFilter = new ExistingDirsFilter() {
            @Override
            public List<File> checkDestDirAndFindExistingDirsAndThrowStopActionIfNone(File destDir,
                                                                                      Collection<File> dirFiles) {
                assertSame(destDir, docTask.getDestinationDir());
                assertSame(dirFiles, docTask.getScalaSrcDirs());
                return docTask.getScalaSrcDirs();
            }
        };
        docTask.setDestinationDir(TEST_TARGET_DIR);

        docTask.setClasspath(new AbstractFileCollection() {
            @Override
            public String getDisplayName() {
                throw new UnsupportedOperationException();
            }

            public Set<File> getFiles() {
                return new LinkedHashSet<File>(TEST_DEPENDENCY_MANAGER_CLASSPATH);
            }
        });
    }

}
