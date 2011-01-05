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

import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.util.JUnit4GroovyMockery
import spock.lang.Specification
import org.gradle.api.internal.artifacts.publish.DefaultPublishArtifact

/**
 * @author Hans Dockter
 */
class DefaultArtifactHandlerTest extends Specification {

    private static final String TEST_CONF_NAME = "someConf"

    private JUnit4GroovyMockery context = new JUnit4GroovyMockery()

    private ConfigurationContainer configurationContainerStub = Mock()
    private PublishArtifactFactory artifactFactoryStub = Mock()
    private Configuration configurationMock = Mock()

    private DefaultArtifactHandler artifactHandler = new DefaultArtifactHandler(configurationContainerStub, artifactFactoryStub)

    void setup() {
        configurationContainerStub.findByName(TEST_CONF_NAME) >> configurationMock
    }

    void pushOneDependency() {
        String someNotation = "someNotation"
        PublishArtifact artifactDummy = Mock()

        when:
        artifactFactoryStub.createArtifact(someNotation) >> artifactDummy
        artifactHandler."$TEST_CONF_NAME"(someNotation)

        then:
        1 * configurationMock.addArtifact(artifactDummy)
    }

    void pushOneDependencyWithClosure() {
        String someNotation = "someNotation"
        DefaultPublishArtifact artifact = new DefaultPublishArtifact("name", "ext", "jar", "classifier", null, new File(""))

        when:
        artifactFactoryStub.createArtifact(someNotation) >> artifact
        artifactHandler."$TEST_CONF_NAME"(someNotation) { type = 'source' }

        then:
        artifact.type == 'source'
        1 * configurationMock.addArtifact(artifact)
    }

    void pushMultipleDependencies() {
        String someNotation1 = "someNotation"
        String someNotation2 = "someNotation2"
        PublishArtifact artifactDummy1 = Mock()
        PublishArtifact artifactDummy2 = Mock()

        when:
        artifactFactoryStub.createArtifact(someNotation1) >> artifactDummy1
        artifactFactoryStub.createArtifact(someNotation2) >> artifactDummy2
        artifactHandler."$TEST_CONF_NAME"(someNotation1, someNotation2)

        then:
        1 * configurationMock.addArtifact(artifactDummy1)
        1 * configurationMock.addArtifact(artifactDummy2)

    }

    void pushToUnknownConfiguration() {
        String unknownConf = TEST_CONF_NAME + "delta"

        when:
        artifactHandler."$unknownConf"("someNotation")
        configurationContainerStub.findByName(unknownConf) >> null

        then:
        thrown(MissingMethodException)
    }
}
