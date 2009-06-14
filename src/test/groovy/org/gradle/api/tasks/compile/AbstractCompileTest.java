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

package org.gradle.api.tasks.compile;

import org.gradle.api.GradleScriptException;
import org.gradle.api.internal.artifacts.AbstractFileCollection;
import org.gradle.api.tasks.AbstractConventionTaskTest;
import org.gradle.api.tasks.util.ExistingDirsFilter;
import org.gradle.util.WrapUtil;
import static org.junit.Assert.*;
import org.junit.Test;

import java.io.File;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Hans Dockter
 */
public abstract class AbstractCompileTest extends AbstractConventionTaskTest {
    public static final String TEST_PATTERN_1 = "pattern1";
    public static final String TEST_PATTERN_2 = "pattern2";
    public static final String TEST_PATTERN_3 = "pattern3";

    public static final File TEST_TARGET_DIR = new File("/targetDir");

    public static final List<File> TEST_DEPENDENCY_MANAGER_CLASSPATH = WrapUtil.toList(new File("jar1"));
    public static final List<String> TEST_INCLUDES = WrapUtil.toList("incl");
    public static final List<String> TEST_EXCLUDES = WrapUtil.toList("excl");

    abstract Compile getCompile();

    @Test public void testCompile() {
        Compile compile = getCompile();
        assertNotNull(compile.getOptions());
        assertNotNull(compile.existentDirsFilter);
        assertNotNull(compile.antCompile);
        assertNull(compile.getDestinationDir());
        assertNull(compile.getSourceCompatibility());
        assertNull(compile.getTargetCompatibility());
        assertNull(compile.getSrcDirs());
    }

    @Test (expected = GradleScriptException.class) public void testExecuteWithUnspecifiedSourceCompatibility() {
        Compile compile = getCompile();
        setUpMocksAndAttributes(compile);
        compile.setSourceCompatibility(null);
        compile.execute();
    }

    @Test (expected = GradleScriptException.class) public void testExecuteWithUnspecifiedTargetCompatibility() {
        Compile compile = getCompile();
        setUpMocksAndAttributes(compile);
        compile.setTargetCompatibility(null);
        compile.execute();
    }

    @Test (expected = GradleScriptException.class) public void testExecuteWithUnspecifiedAntCompile() {
        Compile compile = getCompile();
        setUpMocksAndAttributes(compile);
        compile.antCompile = null;
        compile.execute();
    }

    protected void setUpMocksAndAttributes(final Compile compile) {
        compile.setSrcDirs(WrapUtil.toList(new File("sourceDir1"), new File("sourceDir2")));
        compile.setIncludes(TEST_INCLUDES);
        compile.setExcludes(TEST_EXCLUDES);
        compile.existentDirsFilter = new ExistingDirsFilter(){
            @Override
            public List<File> checkDestDirAndFindExistingDirsAndThrowStopActionIfNone(File destDir,
                                                                                      Collection<File> dirFiles) {
                assertSame(destDir, compile.getDestinationDir());
                assertSame(dirFiles, compile.getSrcDirs());
                return compile.getSrcDirs();
            }
        };
        compile.setSourceCompatibility("1.5");
        compile.setTargetCompatibility("1.5");
        compile.setDestinationDir(TEST_TARGET_DIR);

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

    protected ExistingDirsFilter getGroovyCompileExistingDirsFilterMock(final Compile compile) {
        return new ExistingDirsFilter(){
            @Override
            public List<File> findExistingDirs(Collection<File> dirFiles) {
                if (dirFiles == compile.getSrcDirs()) {
                    return compile.getSrcDirs();
                } else if (dirFiles == compile.property("groovySourceDirs")) {
                    return (List<File>) compile.property("groovySourceDirs");
                }
                fail("srcdirs not passed");
                return null;
            }
        };
    }

    @Test public void testIncludes() {
        Compile compile = getCompile();

        assertSame(compile.include(TEST_PATTERN_1, TEST_PATTERN_2), compile);
        assertEquals(compile.getIncludes(), WrapUtil.toList(TEST_PATTERN_1, TEST_PATTERN_2));

        assertSame(compile.include(TEST_PATTERN_3), compile);
        assertEquals(compile.getIncludes(), WrapUtil.toList(TEST_PATTERN_1, TEST_PATTERN_2, TEST_PATTERN_3));
    }

    @Test public void testExcludes() {
        Compile compile = getCompile();

        assertSame(compile.exclude(TEST_PATTERN_1, TEST_PATTERN_2), compile);
        assertEquals(compile.getExcludes(), WrapUtil.toList(TEST_PATTERN_1, TEST_PATTERN_2));

        assertSame(compile.exclude(TEST_PATTERN_3), compile);
        assertEquals(compile.getExcludes(), WrapUtil.toList(TEST_PATTERN_1, TEST_PATTERN_2, TEST_PATTERN_3));
    }
}
