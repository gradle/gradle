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

import org.gradle.api.internal.initialization.ScriptHandlerFactory;
import org.gradle.api.internal.initialization.ScriptHandlerInternal;
import org.gradle.groovy.scripts.*;
import org.gradle.groovy.scripts.internal.ClasspathScriptTransformer;
import org.gradle.internal.Factory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.logging.LoggingManagerInternal;
import org.jmock.Expectations;
import org.jmock.Sequence;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.URL;
import java.net.URLClassLoader;

import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.sameInstance;

@RunWith(JMock.class)
public class DefaultScriptPluginFactoryTest {
    private final JUnit4Mockery context = new JUnit4Mockery() {{
        setImposteriser(ClassImposteriser.INSTANCE);
    }};
    private final ScriptCompilerFactory scriptCompilerFactoryMock = context.mock(ScriptCompilerFactory.class);
    private final ImportsReader importsReaderMock = context.mock(ImportsReader.class);
    private final ScriptCompiler scriptCompilerMock = context.mock(ScriptCompiler.class);
    private final ScriptSource scriptSourceMock = context.mock(ScriptSource.class);
    private final ScriptRunner scriptRunnerMock = context.mock(ScriptRunner.class, "scriptRunner");
    private final BasicScript scriptMock = context.mock(BasicScript.class);
    private final Instantiator instantiatorMock = context.mock(Instantiator.class);
    private final URLClassLoader parentClassLoader = new URLClassLoader(new URL[0]);
    private final URLClassLoader scriptClassLoader = new URLClassLoader(new URL[0]);
    private final ScriptHandlerFactory scriptHandlerFactoryMock = context.mock(ScriptHandlerFactory.class);
    private final ScriptHandlerInternal scriptHandlerMock = context.mock(ScriptHandlerInternal.class);
    private final ScriptRunner classPathScriptRunnerMock = context.mock(ScriptRunner.class, "classpathScriptRunner");
    private final BasicScript classPathScriptMock = context.mock(BasicScript.class, "classpathScript");
    private final Factory<LoggingManagerInternal> loggingManagerFactoryMock = context.mock(Factory.class);
    private final DefaultScriptPluginFactory factory = new DefaultScriptPluginFactory(scriptCompilerFactoryMock, importsReaderMock, scriptHandlerFactoryMock, parentClassLoader, loggingManagerFactoryMock, instantiatorMock);

    @Test
    public void configuresATargetObjectUsingScript() {
        final Object target = new Object();

        context.checking(new Expectations() {{
            Sequence sequence = context.sequence("seq");
            ScriptSource sourceWithImportsMock = context.mock(ScriptSource.class, "imports");
            LoggingManagerInternal loggingManagerMock = context.mock(LoggingManagerInternal.class);

            one(loggingManagerFactoryMock).create();
            will(returnValue(loggingManagerMock));

            one(importsReaderMock).withImports(scriptSourceMock);
            will(returnValue(sourceWithImportsMock));

            one(scriptCompilerFactoryMock).createCompiler(sourceWithImportsMock);
            will(returnValue(scriptCompilerMock));

            one(scriptHandlerFactoryMock).create(sourceWithImportsMock, parentClassLoader);
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

        ScriptPlugin configurer = factory.create(scriptSourceMock);
        configurer.apply(target);
    }

    @Test
    public void configuresAScriptAwareObjectUsingScript() {
        final ScriptAware target = context.mock(ScriptAware.class);

        context.checking(new Expectations() {{
            Sequence sequence = context.sequence("seq");
            ScriptSource sourceWithImportsMock = context.mock(ScriptSource.class, "imports");
            LoggingManagerInternal loggingManagerMock = context.mock(LoggingManagerInternal.class);

            one(loggingManagerFactoryMock).create();
            will(returnValue(loggingManagerMock));

            one(importsReaderMock).withImports(scriptSourceMock);
            will(returnValue(sourceWithImportsMock));

            one(scriptCompilerFactoryMock).createCompiler(sourceWithImportsMock);
            will(returnValue(scriptCompilerMock));

            allowing(target).beforeCompile(with(notNullValue(ScriptPlugin.class)));

            one(scriptHandlerFactoryMock).create(sourceWithImportsMock, parentClassLoader);
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

            one(target).afterCompile(with(notNullValue(ScriptPlugin.class)), with(sameInstance(scriptMock)));
            inSequence(sequence);

            one(scriptRunnerMock).run();
            inSequence(sequence);
        }});

        ScriptPlugin configurer = factory.create(scriptSourceMock);
        configurer.apply(target);
    }
}