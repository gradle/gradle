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

import org.gradle.groovy.scripts.*;
import org.gradle.api.internal.initialization.ScriptClassLoaderProvider;
import org.gradle.api.internal.artifacts.dsl.InitScriptClasspathScriptTransformer;
import org.gradle.api.internal.artifacts.dsl.InitScriptTransformer;
import org.gradle.api.internal.GradleInternal;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.Expectations;
import org.junit.Test;

import java.net.URLClassLoader;
import java.net.URL;

import groovy.lang.Script;

public class DefaultInitScriptProcessorTest {
    private final JUnit4Mockery context = new JUnit4Mockery();

    @Test
    public void testProcess() {
        final ScriptProcessorFactory scriptProcessorFactoryMock = context.mock(ScriptProcessorFactory.class);
        final ScriptProcessor scriptProcessorMock = context.mock(ScriptProcessor.class);
        final InitScriptMetaData initScriptMetaDataMock = context.mock(InitScriptMetaData.class);
        final ScriptSource initScriptMock = context.mock(ScriptSource.class);
        final GradleInternal gradleMock = context.mock(GradleInternal.class);
        final ScriptClassLoaderProvider buildClassLoaderProviderMock = context.mock(ScriptClassLoaderProvider.class);
        final URLClassLoader classLoader = new URLClassLoader(new URL[0]);
        final Script classPathScriptMock = new EmptyScript();
        final Script buildScriptMock = new EmptyScript();
        context.checking(new Expectations() {{
            one(scriptProcessorFactoryMock).createProcessor(initScriptMock);
            will(returnValue(gradleMock));
            allowing(gradleMock).getClassLoaderProvider();
            will(returnValue(buildClassLoaderProviderMock));
            one(buildClassLoaderProviderMock).getClassLoader();
            will(returnValue(classLoader));
            one(scriptProcessorMock).setClassloader(classLoader);
            one(scriptProcessorMock).setTransformer(with(any(InitScriptClasspathScriptTransformer.class)));
            one(scriptProcessorMock).process(ScriptWithSource.class);
            will(returnValue(classPathScriptMock));
            one(initScriptMetaDataMock).applyMetaData(classPathScriptMock, gradleMock);

            one(buildClassLoaderProviderMock).updateClassPath();

            one(scriptProcessorMock).setTransformer(with(any(InitScriptTransformer.class)));
            one(scriptProcessorMock).process(ScriptWithSource.class);
            will(returnValue(buildScriptMock));
            one(initScriptMetaDataMock).applyMetaData(buildScriptMock, gradleMock);
        }});
    }
}
