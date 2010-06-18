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
import org.gradle.api.internal.tasks.compile.JavaCompiler;
import org.gradle.api.internal.tasks.scala.ScalaCompiler;
import org.gradle.api.tasks.compile.AbstractCompileTest;
import org.gradle.api.tasks.compile.Compile;
import org.gradle.util.GFileUtils;
import org.gradle.util.JUnit4GroovyMockery;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.gradle.util.Matchers.*;

public class ScalaCompileTest extends AbstractCompileTest {

    private ScalaCompile scalaCompile;

    private ScalaCompiler scalaCompiler;
    private JavaCompiler javaCompiler;
    private JUnit4Mockery context = new JUnit4GroovyMockery();

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
        scalaCompile = createTask(ScalaCompile.class);
        scalaCompiler = context.mock(ScalaCompiler.class);
        javaCompiler = context.mock(JavaCompiler.class);
        scalaCompile.setScalaCompiler(scalaCompiler);
        scalaCompile.setJavaCompiler(javaCompiler);

        GFileUtils.touch(new File(srcDir, "incl/file.scala"));
        GFileUtils.touch(new File(srcDir, "incl/file.java"));
    }

    @Test
    public void testExecuteDoingWork() {
        setUpMocksAndAttributes(scalaCompile);
        context.checking(new Expectations() {{
            one(scalaCompiler).setSource(with(hasSameItems(scalaCompile.getSource())));
            one(scalaCompiler).setDestinationDir(scalaCompile.getDestinationDir());
            one(scalaCompiler).setClasspath(scalaCompile.getClasspath());
            one(scalaCompiler).setScalaClasspath(scalaCompile.getScalaClasspath());
            one(scalaCompiler).execute();

            List<File> expectedClassPath = new ArrayList<File>();
            expectedClassPath.add(scalaCompile.getDestinationDir());
            for (File file : scalaCompile.getClasspath()) {
                expectedClassPath.add(file);
            }

            FileCollection javaSrc = scalaCompile.getJavaSrc();
            one(javaCompiler).setSource(with(hasSameItems(javaSrc)));
            one(javaCompiler).setClasspath(expectedClassPath);
            one(javaCompiler).setDestinationDir(scalaCompile.getDestinationDir());
            one(javaCompiler).setDependencyCacheDir(scalaCompile.getDependencyCacheDir());
            one(javaCompiler).setSourceCompatibility(scalaCompile.getSourceCompatibility());
            one(javaCompiler).setTargetCompatibility(scalaCompile.getTargetCompatibility());
            one(javaCompiler).execute();
        }});

        scalaCompile.compile();
    }

    protected void setUpMocksAndAttributes(final ScalaCompile compile) {
        compile.source(srcDir);
        compile.setIncludes(TEST_INCLUDES);
        compile.setExcludes(TEST_EXCLUDES);
        compile.setSourceCompatibility("1.5");
        compile.setTargetCompatibility("1.5");
        compile.setDestinationDir(destDir);
        compile.setDependencyCacheDir(depCacheDir);
        compile.setScalaClasspath(context.mock(FileCollection.class));

        final FileCollection configuration = context.mock(FileCollection.class);
        context.checking(new Expectations(){{
            allowing(configuration).iterator();
            will(returnIterator(TEST_DEPENDENCY_MANAGER_CLASSPATH));
        }});

        compile.setClasspath(configuration);
    }

}
