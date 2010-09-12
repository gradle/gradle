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
package org.gradle.api.internal.artifacts.dsl;

import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.internal.ClassGenerator;
import org.gradle.api.internal.artifacts.ivyservice.ResolverFactory;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertThat;

/**
 * @author Hans Dockter
 */
@RunWith(JMock.class)
public class DefaultRepositoryHandlerFactoryTest {
    private JUnit4Mockery context = new JUnit4Mockery() {{
        setImposteriser(ClassImposteriser.INSTANCE);
    }};
    private ResolverFactory repositoryFactoryMock = context.mock(ResolverFactory.class);
    private ClassGenerator classGeneratorMock = context.mock(ClassGenerator.class);

    @Test
    public void createsARepositoryHandler() {
        final RepositoryHandler repositoryHandlerMock = context.mock(DefaultRepositoryHandler.class);

        context.checking(new Expectations() {{
            one(classGeneratorMock).newInstance(DefaultRepositoryHandler.class, repositoryFactoryMock, classGeneratorMock);
            will(returnValue(repositoryHandlerMock));
        }});

        DefaultRepositoryHandlerFactory repositoryHandlerFactory = new DefaultRepositoryHandlerFactory(
                repositoryFactoryMock, classGeneratorMock);
        DefaultRepositoryHandler repositoryHandler = repositoryHandlerFactory.create();
        assertThat(repositoryHandler, sameInstance((Object) repositoryHandlerMock));
    }

}
