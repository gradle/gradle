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

import org.gradle.api.internal.artifacts.ivyservice.ResolverFactory;
import org.gradle.api.internal.plugins.DefaultConvention;
import org.gradle.api.plugins.Convention;
import org.hamcrest.Matchers;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Hans Dockter
 */
@RunWith(JMock.class)
public class DefaultRepositoryHandlerFactoryTest {
    private JUnit4Mockery context = new JUnit4Mockery();
    private ResolverFactory repositoryFactoryMock = context.mock(ResolverFactory.class);
    private Convention convention = new DefaultConvention();

    @Test
    public void createRepositoryHandler() {
        DefaultRepositoryHandlerFactory repositoryHandlerFactory = new DefaultRepositoryHandlerFactory(repositoryFactoryMock);
        DefaultRepositoryHandler repositoryHandler = repositoryHandlerFactory.createRepositoryHandler(convention);
        assertThat(repositoryHandler.getResolverFactory(), Matchers.sameInstance(repositoryFactoryMock));
        assertThat(repositoryHandler.getConventionAwareHelper().getConvention(), Matchers.sameInstance(convention));
    }
}
