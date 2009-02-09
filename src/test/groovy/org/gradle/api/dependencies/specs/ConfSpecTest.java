/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.api.dependencies.specs;

import org.gradle.api.dependencies.Configuration;
import org.gradle.api.dependencies.Dependency;
import org.gradle.util.WrapUtil;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import static org.junit.Assert.*;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/**
 * @author Hans Dockter
 */
public class ConfSpecTest {
    private DependencyConfigurationSpec confSpec;

    private JUnit4Mockery context = new JUnit4Mockery();

    private static final String TEST_CONF1 = "conf1";
    private static final String TEST_CONF2 = "conf2";
    private static final String TEST_CONF3 = "conf3";

    @Test
    public void init() {
        List<String> testConfs = WrapUtil.toList(TEST_CONF1, TEST_CONF2);
        confSpec = new DependencyConfigurationSpec(true, testConfs.toArray(new String[testConfs.size()]));
        assertEquals(testConfs, confSpec.getConfs());
    }

    @Test
    public void testIsSatisfiedBy() {
        final Dependency dependency = prepareMocks();
        assertTrue(new DependencyConfigurationSpec(true, TEST_CONF1).isSatisfiedBy(dependency));
        assertTrue(new DependencyConfigurationSpec(true, TEST_CONF2).isSatisfiedBy(dependency));
        assertTrue(new DependencyConfigurationSpec(true, TEST_CONF3).isSatisfiedBy(dependency));
        assertFalse(new DependencyConfigurationSpec(true, TEST_CONF1 + "delta").isSatisfiedBy(dependency));
    }

    @Test
    public void testIsSatisfiedByWithoutSuperConfs() {
        final Dependency dependency = prepareMocks();
        assertTrue(new DependencyConfigurationSpec(false, TEST_CONF1).isSatisfiedBy(dependency));
        assertTrue(new DependencyConfigurationSpec(false, TEST_CONF2).isSatisfiedBy(dependency));
        assertFalse(new DependencyConfigurationSpec(false, TEST_CONF3).isSatisfiedBy(dependency));
        assertFalse(new DependencyConfigurationSpec(false, TEST_CONF1 + "delta").isSatisfiedBy(dependency));
    }

    private Dependency prepareMocks() {
        final Dependency dependency = context.mock(Dependency.class);
        final Configuration configurationMock3 = createConfigurationMock(TEST_CONF3);
        final Configuration configurationMock1 = createConfigurationMock(TEST_CONF1, configurationMock3);
        final Configuration configurationMock2 = createConfigurationMock(TEST_CONF2);
        context.checking(new Expectations() {{
            allowing(dependency).getConfigurations(); will(returnValue(WrapUtil.toSet(configurationMock1, configurationMock2)));
        }});
        return dependency;
    }

    private Configuration createConfigurationMock(final String name, final Configuration... superConfs) {
        final Configuration configurationMock = context.mock(Configuration.class, name);
        context.checking(new Expectations() {{
            allowing(configurationMock).getName();
            will(returnValue(name));

            allowing(configurationMock).getChain();
            List<Configuration> chainList = new ArrayList<Configuration>(Arrays.asList(superConfs));
            chainList.add(configurationMock);
            will(returnValue(new HashSet(chainList)));
        }});
        return configurationMock;
    }

    @Test
    public void equality() {
        assertTrue(new DependencyConfigurationSpec(false, TEST_CONF1).equals(new DependencyConfigurationSpec(false, TEST_CONF1)));
        assertFalse(new DependencyConfigurationSpec(true, TEST_CONF1).equals(new DependencyConfigurationSpec(false, TEST_CONF1)));
        assertFalse(new DependencyConfigurationSpec(true, TEST_CONF1).equals(new DependencyConfigurationSpec(true, TEST_CONF2)));
    }
}
