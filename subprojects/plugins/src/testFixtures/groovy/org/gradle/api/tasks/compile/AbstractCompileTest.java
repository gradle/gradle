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

package org.gradle.api.tasks.compile;

import org.gradle.api.internal.file.collections.SimpleFileCollection;
import org.gradle.api.tasks.AbstractConventionTaskTest;
import org.gradle.util.WrapUtil;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.List;

import static org.gradle.util.Matchers.isEmpty;
import static org.junit.Assert.*;

public abstract class AbstractCompileTest extends AbstractConventionTaskTest {
    public static final String TEST_PATTERN_1 = "pattern1";
    public static final String TEST_PATTERN_2 = "pattern2";
    public static final String TEST_PATTERN_3 = "pattern3";

    public static final List<File> TEST_DEPENDENCY_MANAGER_CLASSPATH = WrapUtil.toList(new File("jar1"));
    public static final List<String> TEST_INCLUDES = WrapUtil.toList("incl/*");
    public static final List<String> TEST_EXCLUDES = WrapUtil.toList("excl/*");

    protected File srcDir;
    protected File destDir;
    protected File depCacheDir;

    protected abstract AbstractCompile getCompile();

    @Before
    public final void setUpDirs() {
        destDir = getProject().file("destDir");
        depCacheDir = getProject().file("depCache");
        srcDir = getProject().file("src");
        srcDir.mkdirs();
    }

    @Test public void testDefaults() {
        AbstractCompile compile = getCompile();
        assertNull(compile.getDestinationDir());
        assertNull(compile.getSourceCompatibility());
        assertNull(compile.getTargetCompatibility());
        assertThat(compile.getSource(), isEmpty());
    }

    protected void setUpMocksAndAttributes(final AbstractCompile compile) {
        compile.source(srcDir);
        compile.setIncludes(TEST_INCLUDES);
        compile.setExcludes(TEST_EXCLUDES);
        compile.setSourceCompatibility("1.5");
        compile.setTargetCompatibility("1.5");
        compile.setDestinationDir(destDir);

        compile.setClasspath(new SimpleFileCollection(TEST_DEPENDENCY_MANAGER_CLASSPATH));
    }

    @Test public void testIncludes() {
        AbstractCompile compile = getCompile();

        assertSame(compile.include(TEST_PATTERN_1, TEST_PATTERN_2), compile);
        assertEquals(compile.getIncludes(), WrapUtil.toLinkedSet(TEST_PATTERN_1, TEST_PATTERN_2));

        assertSame(compile.include(TEST_PATTERN_3), compile);
        assertEquals(compile.getIncludes(), WrapUtil.toLinkedSet(TEST_PATTERN_1, TEST_PATTERN_2, TEST_PATTERN_3));
    }

    @Test public void testExcludes() {
        AbstractCompile compile = getCompile();

        assertSame(compile.exclude(TEST_PATTERN_1, TEST_PATTERN_2), compile);
        assertEquals(compile.getExcludes(), WrapUtil.toLinkedSet(TEST_PATTERN_1, TEST_PATTERN_2));

        assertSame(compile.exclude(TEST_PATTERN_3), compile);
        assertEquals(compile.getExcludes(), WrapUtil.toLinkedSet(TEST_PATTERN_1, TEST_PATTERN_2, TEST_PATTERN_3));
    }
}
