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

import org.gradle.api.internal.artifacts.dsl.ClasspathScriptTransformer;
import org.gradle.api.internal.initialization.ScriptHandlerFactory;
import org.gradle.api.internal.initialization.ScriptHandlerInternal;
import org.gradle.api.internal.project.ImportsReader;
import org.gradle.api.internal.project.ServiceRegistry;
import org.gradle.groovy.scripts.*;
import org.jmock.Expectations;
import org.jmock.Sequence;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.URL;
import java.net.URLClassLoader;

import static org.gradle.util.Matchers.*;
import static org.hamcrest.Matchers.*;

@RunWith(JMock.class)
public class DefaultScriptObjectConfigurerFactoryTest {
    private final JUnit4Mockery context = new JUnit4Mockery() {{
        setImposteriser(ClassImposteriser.INSTANCE);
    }};
    private final ScriptCompilerFactory scriptCompilerFactoryMock = context.mock(ScriptCompilerFactory.class);
    private final ImportsReader importsReaderMock = context.mock(ImportsReader.class);
    private final ScriptCompiler scriptCompilerMock = context.mock(ScriptCompiler.class);
    private final ScriptSource scriptSourceMock = context.mock(ScriptSource.class);
    private final ScriptRunner scriptRunnerMock = context.mock(ScriptRunner.class, "scriptRunner");
    private final BasicScript scriptMock = context.mock(BasicScript.class);
    private final URLClassLoader parentClassLoader = new URLClassLoader(new URL[0]);
    private final URLClassLoader scriptClassLoader = new URLClassLoader(new URL[0]);
    private final ScriptHandlerFactory scriptHandlerFactoryMock = context.mock(ScriptHandlerFactory.class);
    private final ScriptHandlerInternal scriptHandlerMock = context.mock(ScriptHandlerInternal.class);
    private final ScriptRunner classPathScriptRunnerMock = context.mock(ScriptRunner.class, "classpathScriptRunner");
    private final BasicScript classPathScriptMock = context.mock(BasicScript.class, "classpathScript");
    private final DefaultScriptObjectConfigurerFactory factory = new DefaultScriptObjectConfigurerFactory(scriptCompilerFactoryMock, importsReaderMock, scriptHandlerFactoryMock, parentClassLoader);

    @Test
    public void configuresATargetObjectUsingScript() {
        final Object target = new Object();

        context.checking(new Expectations() {{
            Sequence sequence = context.sequence("seq");

            one(scriptCompilerFactoryMock).createCompiler(with(reflectionEquals(new ImportsScriptSource(scriptSourceMock, importsReaderMock, null))));
            will(returnValue(scriptCompilerMock));

            one(scriptHandlerFactoryMock).create(parentClassLoader);
            will(returnValue(scriptHandlerMock));

            allowing(scriptHandlerMock).getClassLoader();
            will(returnValue(scriptClassLoader));

            one(scriptCompilerMock).setClassloader(scriptClassLoader);
            inSequence(sequence);

            one(scriptCompilerMock).setTransformer(with(any(ClasspathScriptTransformer.class)));
            inSequence(sequence);

            one(scriptCompilerMock).compile(DefaultScript.class);
            will(returnValue(classPathScriptRunnerMock));

            allowing(classPathScriptRunnerMock).getScript();
            will(returnValue(classPathScriptMock));

            one(classPathScriptMock).init(with(sameInstance(target)), with(notNullValue(ServiceRegistry.class)));
            inSequence(sequence);

            one(classPathScriptRunnerMock).run();
            inSequence(sequence);

            one(scriptHandlerMock).updateClassPath();
            inSequence(sequence);
            
            one(scriptCompilerMock).setTransformer(with(notNullValue(Transformer.class)));
            inSequence(sequence);
            
            one(scriptCompilerMock).compile(DefaultScript.class);
            will(returnValue(scriptRunnerMock));
            inSequence(sequence);

            allowing(scriptRunnerMock).getScript();
            will(returnValue(scriptMock));

            one(scriptMock).init(with(sameInstance(target)), with(notNullValue(ServiceRegistry.class)));
            inSequence(sequence);

            one(scriptRunnerMock).run();
            inSequence(sequence);
        }});

        ScriptObjectConfigurer configurer = factory.create(scriptSourceMock);
        configurer.apply(target);
    }

    @Test
    public void configuresAScriptAwareObjectUsingScript() {
        final ScriptAware target = context.mock(ScriptAware.class);

        context.checking(new Expectations() {{
            Sequence sequence = context.sequence("seq");

            one(scriptCompilerFactoryMock).createCompiler(with(reflectionEquals(new ImportsScriptSource(scriptSourceMock, importsReaderMock, null))));
            will(returnValue(scriptCompilerMock));

            allowing(target).beforeCompile(with(notNullValue(ScriptObjectConfigurer.class)));

            one(scriptHandlerFactoryMock).create(parentClassLoader);
            will(returnValue(scriptHandlerMock));
            
            allowing(scriptHandlerMock).getClassLoader();
            will(returnValue(scriptClassLoader));

            one(scriptCompilerMock).setClassloader(scriptClassLoader);
            inSequence(sequence);

            one(scriptCompilerMock).setTransformer(with(any(ClasspathScriptTransformer.class)));
            inSequence(sequence);

            one(scriptCompilerMock).compile(DefaultScript.class);
            will(returnValue(classPathScriptRunnerMock));

            allowing(classPathScriptRunnerMock).getScript();
            will(returnValue(classPathScriptMock));

            one(classPathScriptMock).init(with(sameInstance(target)), with(notNullValue(ServiceRegistry.class)));
            inSequence(sequence);

            one(classPathScriptRunnerMock).run();
            inSequence(sequence);

            one(scriptHandlerMock).updateClassPath();
            inSequence(sequence);

            one(scriptCompilerMock).setTransformer(with(notNullValue(Transformer.class)));
            inSequence(sequence);

            one(scriptCompilerMock).compile(DefaultScript.class);
            will(returnValue(scriptRunnerMock));
            inSequence(sequence);

            allowing(scriptRunnerMock).getScript();
            will(returnValue(scriptMock));

            one(scriptMock).init(with(sameInstance(target)), with(notNullValue(ServiceRegistry.class)));
            inSequence(sequence);

            one(target).afterCompile(with(notNullValue(ScriptObjectConfigurer.class)), with(sameInstance(scriptMock)));
            inSequence(sequence);

            one(scriptRunnerMock).run();
            inSequence(sequence);
        }});

        ScriptObjectConfigurer configurer = factory.create(scriptSourceMock);
        configurer.apply(target);
    }
}