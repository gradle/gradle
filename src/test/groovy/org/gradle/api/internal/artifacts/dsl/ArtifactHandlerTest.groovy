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
 
package org.gradle.api.internal.artifacts.dsl

import org.junit.Test
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.Configuration
import org.junit.Before
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.util.JUnit4GroovyMockery

/**
 * @author Hans Dockter
 */
class ArtifactHandlerTest {

    private static final String TEST_CONF_NAME = "someConf"

    private JUnit4GroovyMockery context = new JUnit4GroovyMockery()

    private ConfigurationContainer configurationContainerStub = context.mock(ConfigurationContainer)
    private PublishArtifactFactory artifactFactoryStub = context.mock(PublishArtifactFactory)
    private Configuration configurationMock = context.mock(Configuration)

    private ArtifactHandler artifactHandler = new ArtifactHandler(configurationContainerStub, artifactFactoryStub)

    @Before
    void setUp() {
      context.checking {
        allowing(configurationContainerStub).find(TEST_CONF_NAME); will(returnValue(configurationMock))
      }
    }

    @Test
    void pushOneDependency() {
      String someNotation = "someNotation"
      PublishArtifact artifactDummy = context.mock(PublishArtifact)
      context.checking {
        allowing(artifactFactoryStub).createArtifact(someNotation); will(returnValue(artifactDummy))
        one(configurationMock).addArtifact(artifactDummy);
      }
      artifactHandler."$TEST_CONF_NAME"(someNotation)
    }

    @Test
    void pushMultipleDependencies() {
      String someNotation1 = "someNotation"
      String someNotation2 = "someNotation2"
      PublishArtifact artifactDummy1 = context.mock(PublishArtifact, "artifact1")
      PublishArtifact artifactDummy2 = context.mock(PublishArtifact, "artifact2")
      context.checking {
        allowing(artifactFactoryStub).createArtifact(someNotation1); will(returnValue(artifactDummy1))
        allowing(artifactFactoryStub).createArtifact(someNotation2); will(returnValue(artifactDummy2))
        one(configurationMock).addArtifact(artifactDummy1);
        one(configurationMock).addArtifact(artifactDummy2);
      }

      artifactHandler."$TEST_CONF_NAME"(someNotation1, someNotation2)
    }

    @Test(expected = MissingMethodException)
    void pushToUnknownConfiguration() {
      String unknownConf = TEST_CONF_NAME + "delta"
      context.checking {
        allowing(configurationContainerStub).find(unknownConf); will(returnValue(null))
      }
      artifactHandler."$unknownConf"("someNotation")
    }
}
