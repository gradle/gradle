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
package org.gradle.api.internal.artifacts.ivyservice;


import org.gradle.api.artifacts.Module
import org.gradle.api.artifacts.PublishException
import org.gradle.api.artifacts.ResolveException
import org.gradle.api.internal.artifacts.ArtifactPublisher
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal
import org.gradle.util.JUnit4GroovyMockery
import org.jmock.integration.junit4.JMock
import org.junit.Test
import org.junit.runner.RunWith

import static org.hamcrest.Matchers.equalTo
import static org.hamcrest.Matchers.sameInstance
import static org.junit.Assert.assertThat
import static org.junit.Assert.fail

@RunWith(JMock.class)
public class ErrorHandlingArtifactPublisherTest {
    private final JUnit4GroovyMockery context = new JUnit4GroovyMockery();
    private final ArtifactPublisher artifactPublisherMock = context.mock(ArtifactPublisher.class);
    private final ConfigurationInternal configurationMock = context.mock(ConfigurationInternal.class, "<config display name>");
    private final Set configurations = Collections.singleton(configurationMock)
    private final Module moduleMock = context.mock(Module)

    private final RuntimeException failure = new RuntimeException();
    private final ErrorHandlingArtifactPublisher ivyService = new ErrorHandlingArtifactPublisher(artifactPublisherMock);

    @Test
    public void publishDelegatesToBackingService() {
        context.checking {
            one(artifactPublisherMock).publish(moduleMock, configurations, null, null)
        }

        ivyService.publish(moduleMock, configurations, null, null)
    }

    @Test
    public void wrapsPublishException() {
        context.checking {
            one(artifactPublisherMock).publish(moduleMock, configurations, null, null)
            will(throwException(failure))
        }

        try {
            ivyService.publish(moduleMock, configurations, null, null)
            fail()
        }
        catch(PublishException e) {
            assertThat e.message, equalTo("Could not publish '<config display name>'.")
            assertThat(e.cause, sameInstance((Throwable) failure));
        }
    }

    def assertFailsWithResolveException(Closure cl) {
        try {
            cl();
            fail();
        } catch (ResolveException e) {
            assertThat(e.message, equalTo("Could not resolve all dependencies for <config display name>."));
            assertThat(e.cause, sameInstance((Throwable) failure));
        }
    }
}
