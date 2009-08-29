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

import org.gradle.api.ProjectEvaluationListener;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.groovy.scripts.ScriptSource;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JMock.class)
public class DefaultProjectEvaluatorTest {
    private final JUnit4Mockery context = new JUnit4Mockery() {{
        setImposteriser(ClassImposteriser.INSTANCE);
    }};
    private final ProjectInternal project = context.mock(ProjectInternal.class);
    private final ScriptSource scriptSource = context.mock(ScriptSource.class);
    private final ProjectEvaluationListener listener = context.mock(ProjectEvaluationListener.class);
    private final ProjectEvaluator delegate = context.mock(ProjectEvaluator.class, "delegate");
    private final ProjectEvaluator delegate2 = context.mock(ProjectEvaluator.class, "delegate2");
    private final DefaultProjectEvaluator evaluator = new DefaultProjectEvaluator(delegate, delegate2);
    private final GradleInternal gradle = context.mock(GradleInternal.class);

    @Before
    public void setUp() {
        context.checking(new Expectations() {{
            allowing(gradle).getBuildListenerBroadcaster();
            will(returnValue(listener));
            allowing(project).getGradle();
            will(returnValue(gradle));
            allowing(project).getBuildScriptSource();
            will(returnValue(scriptSource));
        }});
    }

    @Test
    public void createsAndExecutesScriptAndNotifiesListener() {
        context.checking(new Expectations(){{
            allowing(gradle).getProjectEvaluationBroadcaster();
            will(returnValue(listener));
        }});

        context.checking(new Expectations() {{
            one(listener).beforeEvaluate(project);

            one(delegate).evaluate(project);

            one(delegate2).evaluate(project);

            one(listener).afterEvaluate(project, null);
        }});

        evaluator.evaluate(project);
    }
}
