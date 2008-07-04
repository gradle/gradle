/*
 * Copyright 2007 the original author or authors.
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

import groovy.mock.interceptor.MockFor;
import org.gradle.Build;
import org.gradle.api.internal.project.BuildScriptFinder;
import org.gradle.api.internal.project.EmbeddedBuildScriptFinder;
import org.gradle.StartParameter;
import org.gradle.Build.BuildFactory;
import org.gradle.util.JUnit4GroovyMockery;
import org.gradle.util.WrapUtil;
import org.junit.runner.RunWith;
import org.jmock.lib.legacy.ClassImposteriser;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.Expectations;
import org.junit.Test;
import org.junit.Before;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Hans Dockter
 */
@RunWith(org.jmock.integration.junit4.JMock.class)
public class EmbeddedBuildExecuterTest {
    EmbeddedBuildExecuter embeddedBuildExecuter;
    BuildFactory buildFactoryMock;
    Build buildMock;
    StartParameter expectedStartParameter;
    File expectedBuildResolverDir;
    String expectedEmbeddedScript;
    JUnit4Mockery context = new JUnit4Mockery();

    @Before
    public void setUp() {
        context.setImposteriser(ClassImposteriser.INSTANCE);
        buildMock = context.mock(Build.class);
        buildFactoryMock = context.mock(BuildFactory.class);
        expectedStartParameter = new StartParameter();
        expectedStartParameter.setCurrentDir(new File("projectDir"));
        expectedStartParameter.setBuildFileName("buildScriptName");
        expectedStartParameter.setTaskNames(WrapUtil.toList("clean", "compile"));
        expectedStartParameter.setGradleUserHomeDir(new File("gradleUserHome"));
        expectedStartParameter.setSystemPropertiesArgs(WrapUtil.toMap("prop1", "prop1"));
        expectedStartParameter.setProjectProperties(WrapUtil.toMap("projectProp1", "projectProp1"));
        embeddedBuildExecuter = new EmbeddedBuildExecuter(buildFactoryMock);
        expectedBuildResolverDir = new File("buildResolverDir");
        expectedEmbeddedScript = "somescript";
    }

    @Test
    public void testExecute() {
        final StartParameter localExpectedStartParameter = StartParameter.newInstance(expectedStartParameter);
        localExpectedStartParameter.setRecursive(true);
        localExpectedStartParameter.setSearchUpwards(true);
        context.checking(new Expectations() {
            {
                exactly(2).of(buildFactoryMock).newInstance(null, expectedBuildResolverDir);
                will(returnValue(buildMock));
                exactly(2).of(buildMock).run(localExpectedStartParameter);
            }
        });
        embeddedBuildExecuter.execute(expectedBuildResolverDir, localExpectedStartParameter);
        embeddedBuildExecuter.execute(expectedBuildResolverDir, localExpectedStartParameter);
    }

    @Test
    public void testExecuteEmbedded() {
        context.checking(new Expectations() {
            {
                exactly(2).of(buildFactoryMock).newInstance(expectedEmbeddedScript, expectedBuildResolverDir);
                will(returnValue(buildMock));
                exactly(2).of(buildMock).runNonRecursivelyWithCurrentDirAsRoot(expectedStartParameter);
            }
        });
        embeddedBuildExecuter.executeEmbeddedScript(expectedBuildResolverDir, expectedEmbeddedScript, expectedStartParameter);
        embeddedBuildExecuter.executeEmbeddedScript(expectedBuildResolverDir, expectedEmbeddedScript, expectedStartParameter);
    }
}
