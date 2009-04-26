/*
 * Copyright 2007-2009 the original author or authors.
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
package org.gradle.api.internal.artifacts.configurations;

import groovy.lang.Closure;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.UnknownConfigurationException;
import org.gradle.api.internal.artifacts.IvyService;
import org.gradle.api.specs.Spec;
import org.gradle.util.HelperUtil;
import org.gradle.util.WrapUtil;
import static org.hamcrest.Matchers.*;
import org.jmock.integration.junit4.JUnit4Mockery;
import static org.junit.Assert.assertThat;
import org.junit.Test;

import java.util.Set;

/**
 * @author Hans Dockter
 */
public class DefaultConfigurationContainerTest {
    private static final String TEST_DESCRIPTION = "testDescription";
    private static final Closure TEST_CLOSURE = HelperUtil.createSetterClosure("Description", TEST_DESCRIPTION);
    private static final String TEST_NAME = "testName";

    private JUnit4Mockery context = new JUnit4Mockery();

    IvyService ivyServiceDummy = context.mock(IvyService.class);
    ResolverProvider resolverProviderDummy = context.mock(ResolverProvider.class);
    DependencyMetaDataProvider dependencyMetaDataProviderDummy = context.mock(DependencyMetaDataProvider.class);

    private DefaultConfigurationContainer configurationContainer = new DefaultConfigurationContainer(
            ivyServiceDummy, resolverProviderDummy, dependencyMetaDataProviderDummy);

    @Test
    public void init() {
        assertThat(configurationContainer.getIvyService(), sameInstance(ivyServiceDummy));
        assertThat(configurationContainer.getResolverProvider(), sameInstance(resolverProviderDummy));
        assertThat(configurationContainer.getDependencyMetaDataProvider(), sameInstance(dependencyMetaDataProviderDummy));
    }

    @Test
    public void testAdd() {
        checkAddGetWithName(configurationContainer.add(TEST_NAME));
    }

    @Test
    public void testAddWithNullClosure() {
        checkAddGetWithName(configurationContainer.add(TEST_NAME, null));
    }

    @Test
    public void testAddWithClosure() {
        Configuration configuration = checkAddGetWithName(configurationContainer.add(TEST_NAME, TEST_CLOSURE));
        assertThat(configuration.getDescription(), equalTo(TEST_DESCRIPTION));
    }

    private Configuration checkAddGetWithName(Configuration configuration) {
        assertThat(configuration, equalTo(configurationContainer.getByName(TEST_NAME)));
        return configuration;
    }

    @Test
    public void testFind() {
        Configuration configuration = configurationContainer.add(TEST_NAME);
        assertThat(configuration, sameInstance(configurationContainer.findByName(TEST_NAME)));
    }

    @Test
    public void testFindNonExisitingConfiguration() {
        assertThat(configurationContainer.findByName(TEST_NAME + "delta"), equalTo(null));
    }

    @Test(expected = UnknownConfigurationException.class)
    public void testGetNonExisitingConfiguration() {
        configurationContainer.getByName(TEST_NAME + "delta");
    }

    @Test
    public void testGetWithClosure() {
        configurationContainer.add(TEST_NAME);
        Configuration configuration = configurationContainer.getByName(TEST_NAME, TEST_CLOSURE);
        assertThat(configuration.getDescription(), equalTo(TEST_DESCRIPTION));
    }

    @Test
    public void testGetWithFilter() {
        Configuration configuration = configurationContainer.add(TEST_NAME);
        configurationContainer.add(TEST_NAME + "delta");
        Set<Configuration> result = configurationContainer.findAll(new Spec<Configuration>() {
            public boolean isSatisfiedBy(Configuration element) {
                return element.getName().equals(TEST_NAME);
            }
        });
        assertThat(result, equalTo(WrapUtil.toSet(configuration)));
    }

    @Test
    public void testGetAll() {
        Configuration configuration1 = configurationContainer.add(TEST_NAME);
        Configuration configuration2 = configurationContainer.add(TEST_NAME + "delta");
        assertThat(configurationContainer.getAll(), equalTo(WrapUtil.toSet(configuration1, configuration2)));
    }

    @Test
    public void testCreateDetached() {
        Dependency dependency1 = HelperUtil.createDependency("group1", "name1", "version1");
        Dependency dependency2 = HelperUtil.createDependency("group2", "name2", "version2");

        Configuration detachedConf = configurationContainer.detachedConfiguration(dependency1, dependency2);

        assertThat(detachedConf.getAll(), equalTo(WrapUtil.toSet(detachedConf)));
        assertThat(detachedConf.getHierarchy(), equalTo(WrapUtil.<Configuration>toList(detachedConf)));
        assertThat(detachedConf.getDependencies(), equalTo(WrapUtil.toSet(dependency1, dependency2)));
        assertNotSameInstances(detachedConf.getDependencies(), WrapUtil.toSet(dependency1, dependency2));
    }

    private void assertNotSameInstances(Set<Dependency> dependencies, Set<Dependency> otherDependencies) {
        for (Dependency dependency : dependencies) {
            assertHasEqualButNotSameInstance(dependency, otherDependencies);
        }
    }

    private void assertHasEqualButNotSameInstance(Dependency dependency, Set<Dependency> otherDependencies) {
        assertThat(otherDependencies, hasItem(dependency));
        for (Dependency otherDependency : otherDependencies) {
            if (otherDependency.equals(dependency)) {
                assertThat(otherDependency, not(sameInstance(dependency)));
            }
        }
    }
}
