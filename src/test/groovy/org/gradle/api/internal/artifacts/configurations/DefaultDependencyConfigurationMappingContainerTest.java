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
package org.gradle.api.internal.artifacts.configurations;

import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.DependencyConfigurationMappingContainer;
import org.gradle.util.WrapUtil;
import org.hamcrest.Matchers;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Hans Dockter
 */
@RunWith(JMock.class)
public class DefaultDependencyConfigurationMappingContainerTest {
    DefaultDependencyConfigurationMappingContainer mappingContainer;

    private JUnit4Mockery context = new JUnit4Mockery();

    @Before
    public void setUp() {
        mappingContainer = new DefaultDependencyConfigurationMappingContainer();   
    }

    @Test
    public void initWithMap() {
        Map<Configuration, List<String>> map = WrapUtil.toMap(createMockConf("a"), WrapUtil.toList("b", "c"));
        assertEquals(map, new DefaultDependencyConfigurationMappingContainer(map).getMappings());
    }
    
    @Test
    public void getMappings() {
        final String testDepConf1 = "depConf1";
        final String testDepConf2 = "depConf2";
        final Configuration testMasterConf = createMockConf("masterConf1");
        final Configuration testMasterConf2 = createMockConf("masterConf2");
        final String testDepConf3 = "depconf3";
        final String testDepConf4 = "depconf4";
        mappingContainer.addMasters(testMasterConf2);
        mappingContainer.add(testDepConf1, testDepConf2);
        mappingContainer.add(WrapUtil.toMap(testMasterConf, WrapUtil.toList(testDepConf3)));
        mappingContainer.add(WrapUtil.toMap(testMasterConf, WrapUtil.toList(testDepConf4)));
        Map<Configuration, List<String>> actualMapping = mappingContainer.getMappings();
        Map<Configuration, List<String>> expectedMapping = new HashMap<Configuration, List<String>>() {{
            put(DependencyConfigurationMappingContainer.WILDCARD, WrapUtil.toList(testDepConf1, testDepConf2));
            put(testMasterConf, WrapUtil.toList(testDepConf3, testDepConf4));
            put(testMasterConf2, WrapUtil.toList(ModuleDescriptor.DEFAULT_CONFIGURATION));
        }};
        assertEquals(expectedMapping, actualMapping);
    }

    private Configuration createMockConf(String name) {
        return context.mock(Configuration.class, name);
    }

    @Test
    public void masterConfs() {
        String testDepConf1 = "a";
        Configuration testMasterConf1 = createMockConf("b");
        Configuration testMasterConf2 = createMockConf("c");
        mappingContainer.add(testDepConf1);
        mappingContainer.addMasters(testMasterConf1);
        mappingContainer.add(WrapUtil.toMap(testMasterConf2, WrapUtil.toList("depConf")));
        assertEquals(WrapUtil.toSet(testMasterConf1, testMasterConf2), mappingContainer.getMasterConfigurations());
    }

    @Test
    public void getDependencyConfigurationMappings() {
        List<String> depConfs = WrapUtil.toList("depConf1", "depConf2");
        String confName = "conf";
        mappingContainer.add(WrapUtil.<Configuration, List<String>>toMap(new DefaultConfiguration(confName, null), depConfs));
        assertThat(mappingContainer.getDependencyConfigurations(confName), Matchers.equalTo(depConfs));
    }

    @Test(expected= InvalidUserDataException.class)
    public void nullWildcardConf() {
        mappingContainer.add("a", null, "b");
    }

    @Test(expected= InvalidUserDataException.class)
    public void nullDefaultMasterConf() {
        mappingContainer.addMasters(createMockConf("a"), null, createMockConf("b"));
    }

    @Test(expected= InvalidUserDataException.class)
    public void nullMasterConf() {
        mappingContainer.add(WrapUtil.<Configuration, List<String>>toMap(null, WrapUtil.toList("a")));
    }

    @Test(expected= InvalidUserDataException.class)
    public void nullDepConf() {
        mappingContainer.add(WrapUtil.toMap(createMockConf("a"), WrapUtil.toList("a", null)));
    }

    @Test(expected= InvalidUserDataException.class)
    public void nullConfList() {
        mappingContainer.add(WrapUtil.<Configuration, List<String>>toMap(createMockConf("a"), null));
    }
}
