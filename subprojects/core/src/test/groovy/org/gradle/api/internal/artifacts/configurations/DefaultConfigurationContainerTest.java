/*
 * Copyright 2010 the original author or authors.
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
import org.gradle.api.internal.DomainObjectContext;
import org.gradle.api.internal.Instantiator;
import org.gradle.api.internal.artifacts.IvyService;
import org.gradle.api.specs.Spec;
import org.gradle.listener.ListenerManager;
import org.gradle.util.HelperUtil;
import org.gradle.util.JUnit4GroovyMockery;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Test;

import java.util.Collection;
import java.util.Set;

import static org.gradle.util.WrapUtil.toList;
import static org.gradle.util.WrapUtil.toSet;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;

/**
 * @author Hans Dockter
 */
public class DefaultConfigurationContainerTest {
    private static final String TEST_DESCRIPTION = "testDescription";
    private static final Closure TEST_CLOSURE = HelperUtil.createSetterClosure("Description", TEST_DESCRIPTION);
    private static final String TEST_NAME = "testName";

    private JUnit4Mockery context = new JUnit4GroovyMockery();

    private IvyService ivyServiceDummy = context.mock(IvyService.class);
    private Instantiator instantiator = context.mock(Instantiator.class);
    private DomainObjectContext domainObjectContext = context.mock(DomainObjectContext.class);
    private ListenerManager listenerManager = context.mock(ListenerManager.class);
    private DefaultConfigurationContainer configurationContainer = new DefaultConfigurationContainer(ivyServiceDummy, instantiator, domainObjectContext, listenerManager);

    @Test
    public void init() {
        assertThat(configurationContainer.getIvyService(), sameInstance(ivyServiceDummy));
    }

    @Test
    public void testAdd() {
        expectConfigurationCreated(TEST_NAME);
        checkAddGetWithName(configurationContainer.add(TEST_NAME));
    }

    @Test
    public void testAddWithNullClosure() {
        expectConfigurationCreated(TEST_NAME);
        checkAddGetWithName(configurationContainer.add(TEST_NAME, null));
    }

    @Test
    public void testAddWithClosure() {
        final Configuration configuration = expectConfigurationCreated(TEST_NAME);
        context.checking(new Expectations(){{
            one(configuration).setDescription(TEST_DESCRIPTION);
        }});
        
        checkAddGetWithName(configurationContainer.add(TEST_NAME, TEST_CLOSURE));
    }

    private Configuration checkAddGetWithName(Configuration configuration) {
        assertThat(configuration, equalTo((Configuration) configurationContainer.getByName(TEST_NAME)));
        return configuration;
    }

    @Test
    public void testFind() {
        expectConfigurationCreated(TEST_NAME);
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
        final Configuration configuration = expectConfigurationCreated(TEST_NAME);
        expectConfigurationCreated(TEST_NAME);
        configurationContainer.add(TEST_NAME);

        context.checking(new Expectations(){{
            one(configuration).setDescription(TEST_DESCRIPTION);
        }});
        configurationContainer.getByName(TEST_NAME, TEST_CLOSURE);
    }

    @Test
    public void testGetWithFilter() {
        expectConfigurationCreated(TEST_NAME);
        expectConfigurationCreated(TEST_NAME + "delta");
        Configuration configuration = configurationContainer.add(TEST_NAME);
        configurationContainer.add(TEST_NAME + "delta");
        Collection<Configuration> result = configurationContainer.findAll(new Spec<Configuration>() {
            public boolean isSatisfiedBy(Configuration element) {
                return element.getName().equals(TEST_NAME);
            }
        });
        assertThat(toList(result), equalTo(toList(toSet(configuration))));
    }

    @Test
    public void testGetAll() {
        expectConfigurationCreated(TEST_NAME);
        expectConfigurationCreated(TEST_NAME + "delta");
        Configuration configuration1 = configurationContainer.add(TEST_NAME);
        Configuration configuration2 = configurationContainer.add(TEST_NAME + "delta");
        assertThat(toList(configurationContainer), equalTo(toList(configuration1, configuration2)));
    }

    @Test
    public void testCreateDetached() {
        Dependency dependency1 = HelperUtil.createDependency("group1", "name1", "version1");
        Dependency dependency2 = HelperUtil.createDependency("group2", "name2", "version2");

        context.checking(new Expectations(){{
            ignoring(listenerManager);
        }});

        Configuration detachedConf = configurationContainer.detachedConfiguration(dependency1, dependency2);

        assertThat(toList(detachedConf.getAll()), equalTo(toList(toSet(detachedConf))));
        assertThat(toList(detachedConf.getHierarchy()), equalTo(toList(toSet(detachedConf))));
        assertThat(detachedConf.getDependencies(), equalTo(toSet(dependency1, dependency2)));
        assertNotSameInstances(detachedConf.getDependencies(), toSet(dependency1, dependency2));
    }

    private Configuration expectConfigurationCreated(final String name) {
        final Configuration configuration = context.mock(ConfigurationInternal.class);
        context.checking(new Expectations() {{
            one(domainObjectContext).absoluteProjectPath(name);
            will(returnValue(name));
            one(instantiator).newInstance(DefaultConfiguration.class, name, name, configurationContainer, ivyServiceDummy, listenerManager);
            will(returnValue(configuration));
            allowing(configuration).getName();
            will(returnValue(name));
        }});
        return configuration;
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
