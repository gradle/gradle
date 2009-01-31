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
package org.gradle.api.internal.dependencies;

import org.junit.Test;
import static org.junit.Assert.assertThat;
import org.gradle.api.dependencies.Configuration;
import org.gradle.api.dependencies.UnknownConfigurationException;
import org.gradle.api.filter.FilterSpec;
import org.gradle.util.HelperUtil;
import org.gradle.util.WrapUtil;
import static org.hamcrest.Matchers.*;
import groovy.lang.Closure;

import java.util.Set;

/**
 * @author Hans Dockter
 */
public class DefaultConfigurationContainerTest {
    private static final String TEST_DESCRIPTION = "testDescription";
    private static final Closure TEST_CLOSURE = HelperUtil.createSetterClosure("Description", TEST_DESCRIPTION);
    private static final String TEST_NAME = "testName";
    private DefaultConfigurationContainer configurationContainer = new DefaultConfigurationContainer();

    @Test
    public void init() {
        Configuration configuration1 = configurationContainer.add(TEST_NAME);
        Configuration configuration2 = configurationContainer.add(TEST_NAME + "delta");
        Set<Configuration> configurationSet = WrapUtil.toSet(configuration1,
                configuration2);
        ConfigurationContainer configurationContainer = new DefaultConfigurationContainer(configurationSet);
        assertThat(configurationContainer.get(), equalTo(configurationSet));
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
        assertThat(configuration, equalTo(configurationContainer.get(TEST_NAME)));
        return configuration;
    }

    @Test
    public void testFind() {
        Configuration configuration = configurationContainer.add(TEST_NAME);
        assertThat(configuration, sameInstance(configurationContainer.find(TEST_NAME)));
    }

    @Test
    public void testFindNonExisitingConfiguration() {
        assertThat(configurationContainer.find(TEST_NAME + "delta"), equalTo(null));
    }

    @Test(expected = UnknownConfigurationException.class)
    public void testGetNonExisitingConfiguration() {
        configurationContainer.get(TEST_NAME + "delta");
    }

    @Test
    public void testGetWithClosure() {
        configurationContainer.add(TEST_NAME);
        Configuration configuration = configurationContainer.get(TEST_NAME, TEST_CLOSURE);
        assertThat(configuration.getDescription(), equalTo(TEST_DESCRIPTION));
    }

    @Test
    public void testGetWithFilter() {
        Configuration configuration = configurationContainer.add(TEST_NAME);
        configurationContainer.add(TEST_NAME + "delta");
        Set<Configuration> result = configurationContainer.get(new FilterSpec<Configuration>() {
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
        assertThat(configurationContainer.get(), equalTo(WrapUtil.toSet(configuration1, configuration2)));
    }

    @Test
    public void testEqualsAndHashCode() {
        Configuration configuration1 = configurationContainer.add(TEST_NAME);
        Configuration configuration2 = configurationContainer.add(TEST_NAME + "delta");
        ConfigurationContainer otherConfigurationContainer = new DefaultConfigurationContainer(WrapUtil.toSet(configuration1,
                configuration2));
        assertThat(configurationContainer, equalTo(otherConfigurationContainer));
        assertThat(configurationContainer.hashCode(), equalTo(otherConfigurationContainer.hashCode()));
        otherConfigurationContainer.add(TEST_NAME + "delta2");
        assertThat(configurationContainer, not(equalTo(otherConfigurationContainer)));
    }
}
