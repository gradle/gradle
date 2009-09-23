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
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.tasks.compile.AbstractCompileTest;
import org.gradle.api.tasks.compile.AntJavac;
import org.gradle.api.tasks.compile.Compile;
import org.gradle.util.GFileUtils;
import static org.gradle.util.Matchers.*;
import static org.hamcrest.Matchers.*;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ScalaCompileTest extends AbstractCompileTest {

    private ScalaCompile scalaCompile;

    private AntScalaCompile antScalaCompileMock;
    private AntJavac antCompileMock;
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
        antCompileMock = context.mock(AntJavac.class);
        scalaCompile.setAntScalaCompile(antScalaCompileMock);
        scalaCompile.setAntCompile(antCompileMock);

        GFileUtils.touch(new File(srcDir, "incl/file.scala"));
        GFileUtils.touch(new File(srcDir, "incl/file.java"));
    }

    @Test
    public void testExecuteDoingWork() {
        setUpMocksAndAttributes(scalaCompile);
        context.checking(new Expectations() {{
            one(antScalaCompileMock).execute(
                    with(hasSameItems(scalaCompile.getSource())),
                    with(equalTo(scalaCompile.getDestinationDir())),
                    with(equalTo(scalaCompile.getClasspath())),
                    with(equalTo(scalaCompile.getScalaCompileOptions())));

            List<File> expectedClassPath = new ArrayList<File>();
            expectedClassPath.add(scalaCompile.getDestinationDir());
            for (File file : scalaCompile.getClasspath()) {
                expectedClassPath.add(file);
            }

            FileCollection javaSrc = scalaCompile.getJavaSrc();
            one(antCompileMock).execute(
                    with(hasSameItems(javaSrc)),
                    with(equalTo(scalaCompile.getDestinationDir())),
                    with(equalTo(scalaCompile.getDependencyCacheDir())),
                    with(equalTo(expectedClassPath)),
                    with(equalTo(scalaCompile.getSourceCompatibility())),
                    with(equalTo(scalaCompile.getTargetCompatibility())),
                    with(equalTo(scalaCompile.getOptions())),
                    with(equalTo(scalaCompile.getAnt())));
        }});

        scalaCompile.compile();
    }

    protected void setUpMocksAndAttributes(final ScalaCompile compile) {
        compile.source(srcDir);
        compile.setIncludes(TEST_INCLUDES);
        compile.setExcludes(TEST_EXCLUDES);
        compile.setTargetCompatibility("1.5");
        compile.setDestinationDir(destDir);
        compile.setDependencyCacheDir(depCacheDir);

        final FileCollection configuration = context.mock(FileCollection.class);
        context.checking(new Expectations(){{
            allowing(configuration).iterator();
            will(returnIterator(TEST_DEPENDENCY_MANAGER_CLASSPATH));
        }});

        compile.setClasspath(configuration);
    }

}
