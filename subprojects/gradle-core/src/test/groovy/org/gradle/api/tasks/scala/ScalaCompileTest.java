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

import org.gradle.api.internal.ConventionTask;
import org.gradle.api.internal.file.AbstractFileCollection;
import org.gradle.api.tasks.compile.AbstractCompileTest;
import org.gradle.api.tasks.compile.Compile;
import org.gradle.api.tasks.util.ExistingDirsFilter;
import org.gradle.util.WrapUtil;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ScalaCompileTest extends AbstractCompileTest {

    private ScalaCompile scalaCompile;

    private AntScalaCompile antScalaCompileMock;

    private JUnit4Mockery context = new JUnit4Mockery();

    @Override
    public Compile getCompile() {
        return scalaCompile;
    }

    @Override
    public ConventionTask getTask() {
        return scalaCompile;
    }

    @Override
    @Before
    public void setUp() {
        super.setUp();
        context.setImposteriser(ClassImposteriser.INSTANCE);
        scalaCompile = createTask(ScalaCompile.class);
        antScalaCompileMock = context.mock(AntScalaCompile.class);
        scalaCompile.setAntScalaCompile(antScalaCompileMock);
    }

    @Test
    public void testScalaIncludes() {
        assertSame(scalaCompile.scalaInclude(TEST_PATTERN_1, TEST_PATTERN_2), scalaCompile);
        assertEquals(scalaCompile.getScalaIncludes(), WrapUtil.toLinkedSet(TEST_PATTERN_1, TEST_PATTERN_2));

        assertSame(scalaCompile.scalaInclude(TEST_PATTERN_3), scalaCompile);
        assertEquals(scalaCompile.getScalaIncludes(), WrapUtil.toLinkedSet(TEST_PATTERN_1, TEST_PATTERN_2, TEST_PATTERN_3));
    }

    @Test
    public void testScalaExcludes() {
        assertSame(scalaCompile.scalaExclude(TEST_PATTERN_1, TEST_PATTERN_2), scalaCompile);
        assertEquals(scalaCompile.getScalaExcludes(), WrapUtil.toLinkedSet(TEST_PATTERN_1, TEST_PATTERN_2));

        assertSame(scalaCompile.scalaExclude(TEST_PATTERN_3), scalaCompile);
        assertEquals(scalaCompile.getScalaExcludes(), WrapUtil.toLinkedSet(TEST_PATTERN_1, TEST_PATTERN_2, TEST_PATTERN_3));
    }

    @Test
    public void testExecuteDoingWork() {
        setUpMocksAndAttributes(scalaCompile);
        context.checking(new Expectations() {{
            one(antScalaCompileMock).execute(scalaCompile.getScalaSrcDirs(), scalaCompile.getScalaIncludes(),
                    scalaCompile.getScalaExcludes(), scalaCompile.getDestinationDir(), scalaCompile.getClasspath(),
                    scalaCompile.getScalaCompileOptions());
        }});
        scalaCompile.execute();
    }

    protected void setUpMocksAndAttributes(final ScalaCompile compile) {
        compile.setSrcDirs(WrapUtil.toList(new File("sourceDir1"), new File("sourceDir2")));
        compile.setScalaSrcDirs(WrapUtil.toList(new File("sourceDir1"), new File("sourceDir2")));
        compile.setIncludes(TEST_INCLUDES);
        compile.setExcludes(TEST_EXCLUDES);
        setupExistingDirsFilter(scalaCompile, new ExistingDirsFilter() {
            @Override
            public List<File> checkDestDirAndFindExistingDirsAndThrowStopActionIfNone(File destDir,
                                                                                      Collection<File> dirFiles) {
                assertSame(destDir, compile.getDestinationDir());
                assertSame(dirFiles, compile.getScalaSrcDirs());
                return compile.getScalaSrcDirs();
            }
        });
        compile.setTargetCompatibility("1.5");
        compile.setDestinationDir(destDir);
        compile.setDependencyCacheDir(depCacheDir);

        compile.setClasspath(new AbstractFileCollection() {
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
