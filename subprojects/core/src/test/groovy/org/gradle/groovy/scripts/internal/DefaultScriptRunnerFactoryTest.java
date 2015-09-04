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
package org.gradle.groovy.scripts.internal;

import org.gradle.api.GradleScriptException;
import org.gradle.groovy.scripts.Script;
import org.gradle.groovy.scripts.ScriptExecutionListener;
import org.gradle.groovy.scripts.ScriptRunner;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.logging.StandardOutputCapture;
import org.hamcrest.Description;
import org.jmock.Expectations;
import org.jmock.Sequence;
import org.jmock.api.Action;
import org.jmock.api.Invocation;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

@RunWith(JMock.class)
public class DefaultScriptRunnerFactoryTest {
    private final JUnit4Mockery context = new JUnit4Mockery() {{
        setImposteriser(ClassImposteriser.INSTANCE);
    }};
    private final CompiledScript<? extends Script, Void> compiledScriptMock = context.mock(CompiledScript.class);
    private final Script scriptMock = context.mock(Script.class, "<script-to-string>");
    private final StandardOutputCapture standardOutputCaptureMock = context.mock(StandardOutputCapture.class);
    private final ClassLoader classLoaderDummy = context.mock(ClassLoader.class);
    private final ScriptSource scriptSourceDummy = context.mock(ScriptSource.class);
    private final ScriptExecutionListener scriptExecutionListenerMock = context.mock(ScriptExecutionListener.class);
    private final Instantiator instantiatorMock = context.mock(Instantiator.class);
    private final Object target = new Object();
    private final ServiceRegistry scriptServices = context.mock(ServiceRegistry.class);
    private final DefaultScriptRunnerFactory factory = new DefaultScriptRunnerFactory(scriptExecutionListenerMock, instantiatorMock);

    @Before
    public void setUp() {
        context.checking(new Expectations() {{
            ignoring(scriptSourceDummy);
        }});
    }

    @Test
    public void doesNotLoadScriptWhenScriptRunnerCreated() {
        ScriptRunner<?, Void> scriptRunner = factory.create(compiledScriptMock, scriptSourceDummy, classLoaderDummy);
        assertThat(scriptRunner, notNullValue());
    }

    @Test
    public void runDoesNothingWhenEmptyScriptIsRun() {
        ScriptRunner<?, Void> scriptRunner = factory.create(compiledScriptMock, scriptSourceDummy, classLoaderDummy);

        context.checking(new Expectations() {{
            allowing(compiledScriptMock).getRunDoesSomething();
            will(returnValue(false));
        }});

        scriptRunner.run(target, scriptServices);
    }

    @Test
    public void setsUpAndTearsDownWhenNonEmptyScriptIsRun() {
        ScriptRunner<?, Void> scriptRunner = factory.create(compiledScriptMock, scriptSourceDummy, classLoaderDummy);

        expectScriptInstantiated();
        context.checking(new Expectations() {{
            Sequence sequence = context.sequence("seq");

            allowing(compiledScriptMock).getRunDoesSomething();
            will(returnValue(true));

            one(scriptExecutionListenerMock).scriptClassLoaded(scriptSourceDummy, Script.class);
            inSequence(sequence);

            one(scriptMock).init(target, scriptServices);
            inSequence(sequence);

            one(standardOutputCaptureMock).start();
            inSequence(sequence);

            one(scriptMock).run();
            inSequence(sequence);
            will(doAll(new Action() {
                public void describeTo(Description description) {
                    description.appendValue("check context classloader");
                }

                public Object invoke(Invocation invocation) throws Throwable {
                    assertThat(Thread.currentThread().getContextClassLoader(), sameInstance(classLoaderDummy));
                    return null;
                }
            }));

            one(standardOutputCaptureMock).stop();
            inSequence(sequence);
        }});

        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        assertThat(originalClassLoader, not(sameInstance(classLoaderDummy)));

        scriptRunner.run(target, scriptServices);

        assertThat(Thread.currentThread().getContextClassLoader(), sameInstance(originalClassLoader));
    }

    @Test
    public void wrapsExecutionExceptionAndRestoresStateWhenScriptFails() {
        final RuntimeException failure = new RuntimeException();

        ScriptRunner<?, Void> scriptRunner = factory.create(compiledScriptMock, scriptSourceDummy, classLoaderDummy);

        expectScriptInstantiated();
        context.checking(new Expectations() {{
            Sequence sequence = context.sequence("seq");

            allowing(compiledScriptMock).getRunDoesSomething();
            will(returnValue(true));

            one(scriptExecutionListenerMock).scriptClassLoaded(scriptSourceDummy, Script.class);
            inSequence(sequence);

            one(scriptMock).init(target, scriptServices);
            inSequence(sequence);

            one(standardOutputCaptureMock).start();
            inSequence(sequence);

            one(scriptMock).run();
            inSequence(sequence);
            will(throwException(failure));

            one(standardOutputCaptureMock).stop();
            inSequence(sequence);
        }});

        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        assertThat(originalClassLoader, not(sameInstance(classLoaderDummy)));

        try {
            scriptRunner.run(target, scriptServices);
            fail();
        } catch (GradleScriptException e) {
            assertThat(e.getMessage(), equalTo("A problem occurred evaluating <script-to-string>."));
            assertThat(e.getCause(), sameInstance((Throwable) failure));
        }

        assertThat(Thread.currentThread().getContextClassLoader(), sameInstance(originalClassLoader));
    }

    void expectScriptInstantiated() {
        context.checking(new Expectations() {{
            allowing(compiledScriptMock).loadClass();
            will(returnValue(Script.class));
            allowing(instantiatorMock).newInstance(Script.class);
            will(returnValue(scriptMock));
            allowing(scriptMock).getStandardOutputCapture();
            will(returnValue(standardOutputCaptureMock));
            allowing(scriptMock).getScriptSource();
            will(returnValue(scriptSourceDummy));
            allowing(scriptMock).getContextClassloader();
            will(returnValue(classLoaderDummy));
            allowing(scriptMock).setScriptSource(scriptSourceDummy);
            allowing(scriptMock).setContextClassloader(classLoaderDummy);
        }});
    }

}
