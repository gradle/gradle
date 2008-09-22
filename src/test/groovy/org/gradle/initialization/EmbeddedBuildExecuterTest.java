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

import org.gradle.Gradle;
import org.gradle.Gradle.GradleFactory;
import org.gradle.StartParameter;
import org.gradle.util.WrapUtil;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

/**
 * @author Hans Dockter
 */
@RunWith(org.jmock.integration.junit4.JMock.class)
public class EmbeddedBuildExecuterTest {
    EmbeddedBuildExecuter embeddedBuildExecuter;
    GradleFactory gradleFactoryMock;
    Gradle gradleMock;
    StartParameter expectedStartParameter;
    File expectedBuildResolverDir;
    String expectedEmbeddedScript;
    JUnit4Mockery context = new JUnit4Mockery();

    @Before
    public void setUp()  {
        context.setImposteriser(ClassImposteriser.INSTANCE);
        gradleMock = context.mock(Gradle.class);
        gradleFactoryMock = context.mock(GradleFactory.class);
        expectedStartParameter = new StartParameter();
        expectedStartParameter.setCurrentDir(new File("projectDir"));
        expectedStartParameter.setBuildFileName("buildScriptName");
        expectedStartParameter.setTaskNames(WrapUtil.toList("clean", "compile"));
        expectedStartParameter.setGradleUserHomeDir(new File("gradleUserHome"));
        expectedStartParameter.setSystemPropertiesArgs(WrapUtil.toMap("prop1", "prop1"));
        expectedStartParameter.setProjectProperties(WrapUtil.toMap("projectProp1", "projectProp1"));
        embeddedBuildExecuter = new EmbeddedBuildExecuter(gradleFactoryMock);
        expectedBuildResolverDir = new File("buildResolverDir");
        expectedEmbeddedScript = "somescript";
    }

    @Test
    public void testExecute() {
        final StartParameter localExpectedStartParameter = expectedStartParameter.newInstance();
        localExpectedStartParameter.setSearchUpwards(true);
        context.checking(new Expectations() {
            {
                exactly(2).of(gradleFactoryMock).newInstance(localExpectedStartParameter);
                will(returnValue(gradleMock));
                exactly(2).of(gradleMock).run();
            }
        });
        embeddedBuildExecuter.execute(localExpectedStartParameter);
        embeddedBuildExecuter.execute(localExpectedStartParameter);
    }
}
