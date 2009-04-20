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

import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.integration.junit4.JMock;
import org.jmock.lib.legacy.ClassImposteriser;
import org.jmock.Expectations;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.gradle.api.internal.project.*;
import org.gradle.api.internal.BuildInternal;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.ProjectEvaluationListener;
import org.gradle.api.GradleScriptException;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.groovy.scripts.IProjectScriptMetaData;
import org.gradle.groovy.scripts.IScriptProcessor;
import org.gradle.groovy.scripts.ImportsScriptSource;
import org.gradle.util.Matchers;

import java.io.File;

@RunWith(JMock.class)
public class DefaultProjectEvaluatorTest {
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
    private final StandardOutputRedirector standardOutputRedirector = context.mock(StandardOutputRedirector.class);
    private final ProjectEvaluationListener listener = context.mock(ProjectEvaluationListener.class);
    private final DefaultProjectEvaluator evaluator = new DefaultProjectEvaluator(importsReader, scriptProcessor, projectScriptMetaData);
    private final BuildInternal build = context.mock(BuildInternal.class);

    @Before
    public void setUp() {
        context.checking(new Expectations() {{
            allowing(project).getBuildScriptSource();
            will(returnValue(scriptSource));

            allowing(project).getRootDir();
            will(returnValue(rootDir));

            allowing(project).getBuildScriptClassLoader();
            will(returnValue(classLoader));

            allowing(project).getStandardOutputRedirector();
            will(returnValue(standardOutputRedirector));

            allowing(scriptSource).getClassName();
            will(returnValue("script class"));

            allowing(scriptSource).getDisplayName();
            will(returnValue("script display name"));
        }});
    }

    @Test
    public void createsAndExecutesScriptAndNotifiesListener() {
        final ScriptSource expectedScriptSource = new ImportsScriptSource(scriptSource, importsReader, rootDir);

        context.checking(new Expectations(){{
            allowing(build).getProjectEvaluationBroadcaster();
            will(returnValue(listener));
        }});
        evaluator.projectsLoaded(build);

        context.checking(new Expectations() {{
            one(listener).beforeEvaluate(project);

            one(scriptProcessor).createScript(
                    with(Matchers.reflectionEquals(expectedScriptSource)),
                    with(same(classLoader)),
                    with(equal(ProjectScript.class)));
            will(returnValue(buildScript));

            one(projectScriptMetaData).applyMetaData(buildScript, project);

            one(project).setBuildScript(buildScript);

            one(standardOutputRedirector).on(LogLevel.QUIET);

            one(buildScript).run();

            one(standardOutputRedirector).flush();

            one(listener).afterEvaluate(project);
        }});

        evaluator.evaluate(project);
    }

    @Test
    public void wrapsEvaluationFailure() {
        final ScriptSource expectedScriptSource = new ImportsScriptSource(scriptSource, importsReader, rootDir);
        final Throwable failure = new RuntimeException();

        context.checking(new Expectations(){{
            allowing(build).getProjectEvaluationBroadcaster();
            will(returnValue(listener));
        }});
        evaluator.projectsLoaded(build);

        context.checking(new Expectations() {{
            one(listener).beforeEvaluate(project);

            one(scriptProcessor).createScript(
                    with(Matchers.reflectionEquals(expectedScriptSource)),
                    with(same(classLoader)),
                    with(equal(ProjectScript.class)));
            will(returnValue(buildScript));

            one(projectScriptMetaData).applyMetaData(buildScript, project);

            one(project).setBuildScript(buildScript);

            one(standardOutputRedirector).on(LogLevel.QUIET);

            one(buildScript).run();
            will(throwException(failure));

            one(standardOutputRedirector).flush();
        }});

        try {
            evaluator.evaluate(project);
            fail();
        } catch (GradleScriptException e) {
            assertThat(e.getOriginalMessage(), equalTo("A problem occurred evaluating " + project + "."));
            assertThat(e.getScriptSource(), sameInstance(scriptSource));
            assertThat(e.getCause(), sameInstance(failure));
        }
    }
}
