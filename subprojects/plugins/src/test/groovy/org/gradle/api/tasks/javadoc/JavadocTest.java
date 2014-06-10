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

package org.gradle.api.tasks.javadoc;

import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.internal.file.collections.SimpleFileCollection;
import org.gradle.api.tasks.AbstractConventionTaskTest;
import org.gradle.api.tasks.javadoc.internal.JavadocSpec;
import org.gradle.external.javadoc.StandardJavadocDocletOptions;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.runtime.jvm.internal.toolchain.JavaToolChainInternal;
import org.gradle.test.fixtures.file.TestFile;
import org.gradle.util.GFileUtils;
import org.gradle.util.JUnit4GroovyMockery;
import org.gradle.util.WrapUtil;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.Set;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;

@RunWith(org.jmock.integration.junit4.JMock.class)
public class JavadocTest extends AbstractConventionTaskTest {
    private final JUnit4Mockery context = new JUnit4GroovyMockery();
    private final TestFile testDir = tmpDir.getTestDirectory();
    private final File destDir = new File(testDir, "dest");
    private final File srcDir = new File(testDir, "srcdir");
    private final Set<File> classpath = WrapUtil.toSet(new File("classpath"));
    private JavaToolChainInternal toolChain = context.mock(JavaToolChainInternal.class);
    private Compiler<JavadocSpec> generator = context.mock(Compiler.class);
    private Javadoc task;
    private FileCollection configurationMock = new SimpleFileCollection(classpath);
    private String executable = "somepath";

    @Before
    public void setUp() {
        task = createTask(Javadoc.class);
        task.setClasspath(configurationMock);
        task.setExecutable(executable);
        task.setToolChain(toolChain);
        GFileUtils.touch(new File(srcDir, "file.java"));
    }

    public ConventionTask getTask() {
        return task;
    }

    private void expectCompilerCreated() {
        context.checking(new Expectations(){{
            one(toolChain).newCompiler(JavadocSpec.class);
            will(returnValue(generator));
        }});
    }

    @Test
    public void defaultExecution() {
        task.setDestinationDir(destDir);
        task.source(srcDir);

        expectCompilerCreated();
        expectJavadocExec();

        task.execute();
    }

    private void expectJavadocExec() {
        context.checking(new Expectations(){{
            one(generator).execute(with(notNullValue(JavadocSpec.class)));
        }});
    }

    @Test
    public void executionWithOptionalAttributes() {
        task.setDestinationDir(destDir);
        task.source(srcDir);
        task.setMaxMemory("max-memory");
        task.setVerbose(true);

        expectCompilerCreated();
        expectJavadocExec();

        task.execute();
    }

    @Test
    public void setsTheWindowAndDocTitleIfNotSet() {
        task.setDestinationDir(destDir);
        task.source(srcDir);
        task.setTitle("title");

        expectCompilerCreated();
        expectJavadocExec();

        task.execute();
        StandardJavadocDocletOptions options = (StandardJavadocDocletOptions) task.getOptions();
        assertThat(options.getDocTitle(), equalTo("title"));
        assertThat(options.getWindowTitle(), equalTo("title"));
    }
}
