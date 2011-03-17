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


import org.gradle.api.internal.artifacts.IvyService
import org.gradle.api.specs.Spec
import org.gradle.util.JUnit4GroovyMockery
import org.jmock.integration.junit4.JMock
import org.junit.Test
import org.junit.runner.RunWith
import org.gradle.api.artifacts.*
import static org.hamcrest.Matchers.equalTo
import static org.hamcrest.Matchers.sameInstance
import static org.junit.Assert.assertThat
import static org.junit.Assert.fail

@RunWith(JMock.class)
public class ErrorHandlingIvyServiceTest {
    private final JUnit4GroovyMockery context = new JUnit4GroovyMockery();
    private final IvyService ivyServiceMock = context.mock(IvyService.class);
    private final ResolvedConfiguration resolvedConfigurationMock = context.mock(ResolvedConfiguration.class);
    private final Configuration configurationMock = context.mock(Configuration.class, "<config display name>");
    private final Spec<Dependency> specDummy = context.mock(Spec.class);
    private final RuntimeException failure = new RuntimeException();
    private final ErrorHandlingIvyService ivyService = new ErrorHandlingIvyService(ivyServiceMock);

    @Test
    public void resolveDelegatesToBackingService() {
        context.checking {
            one(ivyServiceMock).resolve(configurationMock);
            will(returnValue(resolvedConfigurationMock));
        }

        ResolvedConfiguration resolvedConfiguration = ivyService.resolve(configurationMock);

        context.checking {
            one(resolvedConfigurationMock).hasError();
            one(resolvedConfigurationMock).rethrowFailure();
            one(resolvedConfigurationMock).getFiles(specDummy);
        }

        resolvedConfiguration.hasError();
        resolvedConfiguration.rethrowFailure();
        resolvedConfiguration.getFiles(specDummy);
    }

    @Test
    public void returnsAnExceptionThrowingConfigurationWhenResolveFails() {

        context.checking {
            one(ivyServiceMock).resolve(configurationMock);
            will(throwException(failure));
        }

        ResolvedConfiguration resolvedConfiguration = ivyService.resolve(configurationMock);

        assertThat(resolvedConfiguration.hasError(), equalTo(true));

        assertFailsWithResolveException {
            resolvedConfiguration.rethrowFailure();
        }
        assertFailsWithResolveException {
            resolvedConfiguration.getFiles(specDummy);
        }
        assertFailsWithResolveException {
            resolvedConfiguration.getFirstLevelModuleDependencies();
        }
        assertFailsWithResolveException {
            resolvedConfiguration.getResolvedArtifacts();
        }
    }

    @Test
    public void wrapsExceptionsThrownByResolvedConfiguration() {
        context.checking {
            one(ivyServiceMock).resolve(configurationMock);
            will(returnValue(resolvedConfigurationMock));
        }

        ResolvedConfiguration resolvedConfiguration = ivyService.resolve(configurationMock);

        context.checking {
            allowing(resolvedConfigurationMock).rethrowFailure()
            will(throwException(failure))
            allowing(resolvedConfigurationMock).getFiles(specDummy)
            will(throwException(failure))
            allowing(resolvedConfigurationMock).getFirstLevelModuleDependencies()
            will(throwException(failure))
            allowing(resolvedConfigurationMock).getResolvedArtifacts()
            will(throwException(failure))
        }

        assertFailsWithResolveException {
            resolvedConfiguration.rethrowFailure();
        }
        assertFailsWithResolveException {
            resolvedConfiguration.getFiles(specDummy);
        }
        assertFailsWithResolveException {
            resolvedConfiguration.getFirstLevelModuleDependencies();
        }
        assertFailsWithResolveException {
            resolvedConfiguration.getResolvedArtifacts();
        }
    }

    @Test
    public void publishDelegatesToBackingService() {
        context.checking {
            one(ivyServiceMock).publish([configurationMock] as Set, null, [])
        }

        ivyService.publish([configurationMock] as Set, null, [])
    }

    @Test
    public void wrapsPublishException() {
        context.checking {
            one(ivyServiceMock).publish([configurationMock] as Set, null, [])
            will(throwException(failure))
        }

        try {
            ivyService.publish([configurationMock] as Set, null, [])
            fail()
        }
        catch(PublishException e) {
            assertThat e.message, equalTo("Could not publish configurations [<config display name>].")
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
