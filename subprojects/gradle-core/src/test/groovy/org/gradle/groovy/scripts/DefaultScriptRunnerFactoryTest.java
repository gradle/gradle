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
package org.gradle.groovy.scripts;

import static org.hamcrest.Matchers.*;
import org.hamcrest.Description;
import org.jmock.Expectations;
import org.jmock.api.Action;
import org.jmock.api.Invocation;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.gradle.api.internal.project.StandardOutputRedirector;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.GradleScriptException;

@RunWith(JMock.class)
public class DefaultScriptRunnerFactoryTest {
    private final JUnit4Mockery context = new JUnit4Mockery() {{
        setImposteriser(ClassImposteriser.INSTANCE);
    }};
    private final Script scriptMock = context.mock(Script.class, "<script-to-string>");
    private final ScriptMetaData scriptMetaDataMock = context.mock(ScriptMetaData.class);
    private final StandardOutputRedirector standardOutputRedirectorMock = context.mock(StandardOutputRedirector.class);
    private final ClassLoader classLoaderDummy = context.mock(ClassLoader.class);
    private final ScriptSource scriptSourceDummy = context.mock(ScriptSource.class);
    private final DefaultScriptRunnerFactory factory = new DefaultScriptRunnerFactory(scriptMetaDataMock);

    @Before
    public void setUp() {
        context.checking(new Expectations() {{
            allowing(scriptMock).getStandardOutputRedirector();
            will(returnValue(standardOutputRedirectorMock));
            allowing(scriptMock).getScriptSource();
            will(returnValue(scriptSourceDummy));
            allowing(scriptMock).getContextClassloader();
            will(returnValue(classLoaderDummy));
            ignoring(scriptSourceDummy);
        }});
    }

    @Test
    public void createsScriptRunner() {
        ScriptRunner<Script> scriptRunner = factory.create(scriptMock);
        assertThat(scriptRunner.getScript(), sameInstance(scriptMock));
    }

    @Test
    public void appliesMetaDataToScriptWhenDelegateIsSet() {
        final Object delegate = new Object();

        ScriptRunner<Script> scriptRunner = factory.create(scriptMock);

        context.checking(new Expectations() {{
            one(scriptMetaDataMock).applyMetaData(scriptMock, delegate);
            one(scriptMock).setScriptTarget(delegate);
        }});

        scriptRunner.setDelegate(delegate);
    }

    @Test
    public void redirectsStandardOutputAndSetsContextClassLoaderWhenScriptIsRun() {
        ScriptRunner<Script> scriptRunner = factory.create(scriptMock);

        context.checking(new Expectations() {{
            one(standardOutputRedirectorMock).on(LogLevel.QUIET);
            one(scriptMock).run();
            will(doAll(new Action() {
                public void describeTo(Description description) {
                    description.appendValue("check context classloader");
                }

                public Object invoke(Invocation invocation) throws Throwable {
                    assertThat(Thread.currentThread().getContextClassLoader(), sameInstance(classLoaderDummy));
                    return null;
                }
            }));
            one(standardOutputRedirectorMock).flush();
            one(standardOutputRedirectorMock).off();
        }});

        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        assertThat(originalClassLoader, not(sameInstance(classLoaderDummy)));

        scriptRunner.run();

        assertThat(Thread.currentThread().getContextClassLoader(), sameInstance(originalClassLoader));
    }

    @Test
    public void wrapsExecutionExceptionAndRestoresStateWhenScriptFails() {
        final RuntimeException failure = new RuntimeException();

        ScriptRunner<Script> scriptRunner = factory.create(scriptMock);

        context.checking(new Expectations() {{
            one(standardOutputRedirectorMock).on(LogLevel.QUIET);
            one(scriptMock).run();
            will(throwException(failure));
            one(standardOutputRedirectorMock).flush();
            one(standardOutputRedirectorMock).off();
        }});

        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        assertThat(originalClassLoader, not(sameInstance(classLoaderDummy)));

        try {
            scriptRunner.run();
            fail();
        } catch (GradleScriptException e) {
            assertThat(e.getOriginalMessage(), equalTo("A problem occurred evaluating <script-to-string>."));
            assertThat(e.getScriptSource(), sameInstance(scriptSourceDummy));
            assertThat(e.getCause(), sameInstance((Throwable) failure));
        }

        assertThat(Thread.currentThread().getContextClassLoader(), sameInstance(originalClassLoader));
    }
}
