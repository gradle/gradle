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

import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectScript;
import org.gradle.groovy.scripts.ScriptSource;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JMock.class)
public class BuildScriptProcessorTest {
    private final JUnit4Mockery context = new JUnit4Mockery() {{
        setImposteriser(ClassImposteriser.INSTANCE);
    }};
    private final ProjectInternal project = context.mock(ProjectInternal.class);
    private final ScriptSource scriptSource = context.mock(ScriptSource.class);
    private final ScriptObjectConfigurerFactory configurerFactory = context.mock(ScriptObjectConfigurerFactory.class);
    private final ScriptObjectConfigurer scriptObjectConfigurer = context.mock(ScriptObjectConfigurer.class);
    private final BuildScriptProcessor evaluator = new BuildScriptProcessor(configurerFactory);

    @Before
    public void setUp() {
        context.checking(new Expectations() {{
            allowing(project).getBuildScriptSource();
            will(returnValue(scriptSource));
        }});
    }

    @Test
    public void configuresProjectUsingBuildScript() {
        context.checking(new Expectations() {{
            one(configurerFactory).create(scriptSource);
            will(returnValue(scriptObjectConfigurer));

            one(scriptObjectConfigurer).setClasspathClosureName("buildscript");
            one(scriptObjectConfigurer).setScriptBaseClass(ProjectScript.class);
            one(scriptObjectConfigurer).apply(project);
        }});

        evaluator.evaluate(project);
    }
}