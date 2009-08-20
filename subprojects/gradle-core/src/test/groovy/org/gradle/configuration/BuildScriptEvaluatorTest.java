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

import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectScript;
import org.gradle.api.internal.project.StandardOutputRedirector;
import org.gradle.api.logging.LogLevel;
import org.gradle.groovy.scripts.ScriptSource;
import static org.hamcrest.Matchers.*;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JMock.class)
public class BuildScriptEvaluatorTest {
    private final JUnit4Mockery context = new JUnit4Mockery() {{
        setImposteriser(ClassImposteriser.INSTANCE);
    }};
    private final ProjectInternal project = context.mock(ProjectInternal.class);
    private final ScriptSource scriptSource = context.mock(ScriptSource.class);
    private final ProjectScript buildScript = context.mock(ProjectScript.class);
    private final StandardOutputRedirector standardOutputRedirector = context.mock(StandardOutputRedirector.class);
    private final BuildScriptEvaluator evaluator = new BuildScriptEvaluator();

    @Before
    public void setUp() {
        context.checking(new Expectations() {{
            allowing(project).getBuildScriptSource();
            will(returnValue(scriptSource));

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
        context.checking(new Expectations() {{
            one(project).getScript();
            will(returnValue(buildScript));

            one(standardOutputRedirector).on(LogLevel.QUIET);

            one(buildScript).run();

            one(standardOutputRedirector).flush();
        }});

        evaluator.evaluate(project);
    }

    @Test
    public void flushesOnEvaluationFailure() {
        final Throwable failure = new RuntimeException();

        context.checking(new Expectations() {{
            one(project).getScript();
            will(returnValue(buildScript));

            one(standardOutputRedirector).on(LogLevel.QUIET);

            one(buildScript).run();
            will(throwException(failure));

            one(standardOutputRedirector).flush();
        }});

        try {
            evaluator.evaluate(project);
            fail();
        } catch (RuntimeException e) {
            assertThat(e, sameInstance(failure));
        }
    }
}