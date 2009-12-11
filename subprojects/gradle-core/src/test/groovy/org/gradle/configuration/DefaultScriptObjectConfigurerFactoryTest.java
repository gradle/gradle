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
import org.gradle.api.internal.initialization.ScriptClassLoaderProvider;
import org.gradle.api.internal.project.ImportsReader;
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
    private final URLClassLoader defaultClassLoader = new URLClassLoader(new URL[0]);
    private final DefaultScriptObjectConfigurerFactory factory = new DefaultScriptObjectConfigurerFactory(scriptCompilerFactoryMock, importsReaderMock);

    @Test
    public void configuresATargetObjectUsingScript() {
        final Object target = new Object();

        context.checking(new Expectations() {{
            Sequence sequence = context.sequence("seq");

            one(scriptCompilerFactoryMock).createCompiler(with(reflectionEquals(new ImportsScriptSource(scriptSourceMock, importsReaderMock, null))));
            will(returnValue(scriptCompilerMock));

            one(scriptCompilerMock).setClassloader(defaultClassLoader);
            inSequence(sequence);

            one(scriptCompilerMock).compile(BasicScript.class);
            will(returnValue(scriptRunnerMock));
            inSequence(sequence);

            one(scriptRunnerMock).setDelegate(target);
            inSequence(sequence);
            one(scriptRunnerMock).run();
            inSequence(sequence);
        }});

        ScriptObjectConfigurer configurer = factory.create(scriptSourceMock);
        configurer.setClassLoader(defaultClassLoader);
        configurer.apply(target);
    }

    @Test
    public void configuresAScriptAwareObjectUsingScript() {
        final ScriptClassLoaderProvider buildClassLoaderProviderMock = context.mock(ScriptClassLoaderProvider.class);
        final ScriptAware target = context.mock(ScriptAware.class);
        final URLClassLoader classLoader = new URLClassLoader(new URL[0]);
        final ScriptRunner classPathScriptRunnerMock = context.mock(ScriptRunner.class, "classpathScriptRunner");
        final Script scriptMock = context.mock(Script.class);

        context.checking(new Expectations() {{
            Sequence sequence = context.sequence("seq");

            one(scriptCompilerFactoryMock).createCompiler(with(reflectionEquals(new ImportsScriptSource(scriptSourceMock, importsReaderMock, null))));
            will(returnValue(scriptCompilerMock));

            allowing(target).getClassLoaderProvider();
            will(returnValue(buildClassLoaderProviderMock));
            
            allowing(buildClassLoaderProviderMock).getClassLoader();
            will(returnValue(classLoader));

            one(scriptCompilerMock).setClassloader(classLoader);
            inSequence(sequence);

            one(scriptCompilerMock).setTransformer(with(any(ClasspathScriptTransformer.class)));
            inSequence(sequence);

            one(scriptCompilerMock).compile(BasicScript.class);
            will(returnValue(classPathScriptRunnerMock));

            one(classPathScriptRunnerMock).setDelegate(target);
            inSequence(sequence);

            one(classPathScriptRunnerMock).run();
            inSequence(sequence);

            one(buildClassLoaderProviderMock).updateClassPath();
            inSequence(sequence);

            one(scriptCompilerMock).setTransformer(with(notNullValue(Transformer.class)));
            inSequence(sequence);

            one(scriptCompilerMock).compile(BasicScript.class);
            will(returnValue(scriptRunnerMock));
            inSequence(sequence);

            one(scriptRunnerMock).setDelegate(target);
            inSequence(sequence);

            allowing(scriptRunnerMock).getScript();
            will(returnValue(scriptMock));

            one(target).setScript(scriptMock);
            inSequence(sequence);

            one(scriptRunnerMock).run();
            inSequence(sequence);
        }});

        ScriptObjectConfigurer configurer = factory.create(scriptSourceMock);
        configurer.apply(target);
    }
}