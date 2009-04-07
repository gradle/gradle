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
import org.gradle.api.tasks.ConventionValue;
import org.gradle.util.WrapUtil;
import org.hamcrest.Matchers;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import static org.junit.Assert.assertThat;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Hans Dockter
 */
@RunWith(JMock.class)
public class DefaultRepositoryHandlerFactoryTest {
    private JUnit4Mockery context = new JUnit4Mockery();
    private ResolverFactory repositoryFactoryMock = context.mock(ResolverFactory.class);
    private Convention convention = new DefaultConvention();
    @Test
    public void init() {
        assertThat((HashMap<String, ConventionValue>) new DefaultRepositoryHandlerFactory(
                repositoryFactoryMock).getConventionMapping(),
                Matchers.equalTo(new HashMap<String, ConventionValue>()));   
    }

    @Test
    public void createRepositoryHandler() {
        Map<String, ConventionValue> mavenConventionMapping = WrapUtil.toMap("key", context.mock(ConventionValue.class));
        DefaultRepositoryHandlerFactory repositoryHandlerFactory =
                new DefaultRepositoryHandlerFactory(repositoryFactoryMock);
        repositoryHandlerFactory.setConvention(convention);
        repositoryHandlerFactory.setConventionMapping(mavenConventionMapping);
        RepositoryHandler repositoryHandler = repositoryHandlerFactory.createRepositoryHandler();
        assertThat(repositoryHandler.getResolverFactory(), Matchers.sameInstance(repositoryFactoryMock));
        assertThat(repositoryHandler.getConventionMapping(), Matchers.equalTo(mavenConventionMapping));
        assertThat(repositoryHandler.getConventionAwareHelper().getConvention(),
                Matchers.sameInstance(convention));
    }


}
