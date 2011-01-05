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
package org.gradle.initialization;

import org.junit.Test;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.Expectations;
import org.gradle.configuration.InitScriptProcessor;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.api.internal.GradleInternal;

import java.util.List;
import java.util.ArrayList;

public class InitScriptHandlerTest {

    @Test
    public void testExecuteScripts() {
        JUnit4Mockery context = new JUnit4Mockery();

        final InitScriptFinder finderMock = context.mock(InitScriptFinder.class);
        final InitScriptProcessor processorMock = context.mock(InitScriptProcessor.class);
        final GradleInternal gradleMock = context.mock(GradleInternal.class);
        final ScriptSource source1Mock = context.mock(ScriptSource.class, "source 1");
        final ScriptSource source2Mock = context.mock(ScriptSource.class, "source 2");
        final List<ScriptSource> testSources = new ArrayList<ScriptSource>();
        testSources.add(source1Mock);
        testSources.add(source2Mock);

        context.checking(new Expectations() {{
            one(finderMock).findScripts(gradleMock);
            will(returnValue(testSources));
            one(processorMock).process(source1Mock, gradleMock);
            one(processorMock).process(source2Mock, gradleMock);
        }});

        InitScriptHandler handler = new InitScriptHandler(finderMock, processorMock);
        handler.executeScripts(gradleMock);
    }
}
