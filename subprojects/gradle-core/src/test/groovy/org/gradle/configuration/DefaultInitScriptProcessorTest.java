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

import org.gradle.api.internal.GradleInternal;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.initialization.InitScript;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JMock.class)
public class DefaultInitScriptProcessorTest {
    private final JUnit4Mockery context = new JUnit4Mockery();

    @Test
    public void testProcess() {
        final ScriptPluginFactory scriptPluginFactory = context.mock(ScriptPluginFactory.class);
        final ScriptPlugin configurer = context.mock(ScriptPlugin.class);
        final ScriptSource initScriptMock = context.mock(ScriptSource.class);
        final GradleInternal gradleMock = context.mock(GradleInternal.class);

        context.checking(new Expectations() {{
            one(scriptPluginFactory).create(initScriptMock);
            will(returnValue(configurer));

            one(configurer).setClasspathClosureName("initscript");
            one(configurer).setScriptBaseClass(InitScript.class);

            one(configurer).apply(gradleMock);
        }});

        DefaultInitScriptProcessor processor = new DefaultInitScriptProcessor(scriptPluginFactory);
        processor.process(initScriptMock, gradleMock);
    }
}
