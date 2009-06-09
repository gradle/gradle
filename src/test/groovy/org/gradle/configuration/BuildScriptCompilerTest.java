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
package org.gradle.configuration;

import org.gradle.api.internal.project.ImportsReader;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectScript;
import org.gradle.groovy.scripts.IProjectScriptMetaData;
import org.gradle.groovy.scripts.IScriptProcessor;
import org.gradle.groovy.scripts.ImportsScriptSource;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.util.Matchers;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

@RunWith(JMock.class)
public class BuildScriptCompilerTest {
    private final JUnit4Mockery context = new JUnit4Mockery() {{
        setImposteriser(ClassImposteriser.INSTANCE);
    }};
    private final ProjectInternal project = context.mock(ProjectInternal.class);
    private final ScriptSource scriptSource = context.mock(ScriptSource.class);
    private final IScriptProcessor scriptProcessor = context.mock(IScriptProcessor.class);
    private final IProjectScriptMetaData projectScriptMetaData = context.mock(IProjectScriptMetaData.class);
    private final ImportsReader importsReader = context.mock(ImportsReader.class);
    private final ClassLoader classLoader = context.mock(ClassLoader.class);
    private final ProjectScript buildScript = context.mock(ProjectScript.class);
    private final File rootDir = new File("root dir");
    private final BuildScriptCompiler evaluator = new BuildScriptCompiler(importsReader, scriptProcessor,
            projectScriptMetaData);

    @Before
    public void setUp() {
        context.checking(new Expectations() {{
            allowing(project).getBuildScriptSource();
            will(returnValue(scriptSource));

            allowing(project).getRootDir();
            will(returnValue(rootDir));

            allowing(project).getBuildScriptClassLoader();
            will(returnValue(classLoader));
        }});
    }

    @Test
    public void createsAndExecutesScriptAndNotifiesListener() {
        final ScriptSource expectedScriptSource = new ImportsScriptSource(scriptSource, importsReader, rootDir);

        context.checking(new Expectations() {{
            one(scriptProcessor).createScript(with(Matchers.reflectionEquals(expectedScriptSource)), with(same(
                    classLoader)), with(equal(ProjectScript.class)));
            will(returnValue(buildScript));

            one(projectScriptMetaData).applyMetaData(buildScript, project);

            one(project).setBuildScript(buildScript);
        }});

        evaluator.evaluate(project);
    }
}