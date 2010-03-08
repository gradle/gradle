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
package org.gradle.configuration;

import org.gradle.api.ProjectEvaluationListener;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectStateInternal;
import org.gradle.util.JUnit4GroovyMockery;
import org.hamcrest.Matchers;
import org.jmock.Expectations;
import org.jmock.Sequence;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

@RunWith(JMock.class)
public class DefaultProjectEvaluatorTest {
    private final JUnit4Mockery context = new JUnit4GroovyMockery();
    private final ProjectInternal project = context.mock(ProjectInternal.class);
    private final ProjectEvaluationListener listener = context.mock(ProjectEvaluationListener.class);
    private final ProjectEvaluator delegate = context.mock(ProjectEvaluator.class, "delegate");
    private final ProjectStateInternal state = context.mock(ProjectStateInternal.class);
    private final DefaultProjectEvaluator evaluator = new DefaultProjectEvaluator(delegate);

    @Before
    public void setUp() {
        context.checking(new Expectations() {{
            allowing(project).getProjectEvaluationBroadcaster();
            will(returnValue(listener));
        }});
    }

    @Test
    public void doesNothingWhenProjectHasAlreadyBeenExecuted() {
        context.checking(new Expectations() {{
            allowing(state).getExecuted();
            will(returnValue(true));
        }});

        evaluator.evaluate(project, state);
    }
    
    @Test
    public void createsAndExecutesScriptAndNotifiesListener() {
        context.checking(new Expectations() {{
            allowing(state).getExecuted();
            will(returnValue(false));

            Sequence sequence = context.sequence("seq");

            one(listener).beforeEvaluate(project);
            inSequence(sequence);

            one(state).setExecuting(true);
            inSequence(sequence);

            one(delegate).evaluate(project, state);
            inSequence(sequence);

            one(state).setExecuting(false);
            inSequence(sequence);

            one(state).executed();
            inSequence(sequence);

            one(listener).afterEvaluate(project, state);
            inSequence(sequence);
        }});

        evaluator.evaluate(project, state);
    }

    @Test
    public void notifiesListenerOnFailure() {
        final RuntimeException failure = new RuntimeException();

        context.checking(new Expectations() {{
            allowing(state).getExecuted();
            will(returnValue(false));

            Sequence sequence = context.sequence("seq");

            one(listener).beforeEvaluate(project);
            inSequence(sequence);

            one(state).setExecuting(true);
            inSequence(sequence);

            one(delegate).evaluate(project, state);
            will(throwException(failure));
            inSequence(sequence);

            one(state).setExecuting(false);
            inSequence(sequence);

            one(state).executed();
            inSequence(sequence);
            
            one(listener).afterEvaluate(project, state);
            inSequence(sequence);
        }});

        try {
            evaluator.evaluate(project, state);
            fail();
        } catch (RuntimeException e) {
            assertThat(e, Matchers.sameInstance(failure));
        }
    }
}
