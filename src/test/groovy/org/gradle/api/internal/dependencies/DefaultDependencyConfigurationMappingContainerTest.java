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
package org.gradle.api.internal.dependencies;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import org.gradle.util.WrapUtil;
import org.gradle.api.InvalidUserDataException;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * @author Hans Dockter
 */
public class DefaultDependencyConfigurationMappingContainerTest {
    DefaultDependencyConfigurationMappingContainer mappingContainer;
    
    @Before
    public void setUp() {
        mappingContainer = new DefaultDependencyConfigurationMappingContainer();   
    }
    
    @Test
    public void getMappings() {
        final String testDepConf1 = "depConf1";
        final String testDepConf2 = "depConf2";
        final String testMasterConf = "masterConf1";
        final String testMasterConf2 = "masterConf2";
        final String testDepConf3 = "depconf3";
        final String testDepConf4 = "depconf4";
        mappingContainer.addMasters(testMasterConf2);
        mappingContainer.add(testDepConf1, testDepConf2);
        mappingContainer.add(WrapUtil.toMap(testMasterConf, WrapUtil.toList(testDepConf3)));
        mappingContainer.add(WrapUtil.toMap(testMasterConf, WrapUtil.toList(testDepConf4)));
        Map<String, List<String>> actualMapping = mappingContainer.getMappings();
        Map<String, List<String>> expectedMapping = new HashMap<String, List<String>>() {{
            put("*", WrapUtil.toList(testDepConf1, testDepConf2));
            put(testMasterConf, WrapUtil.toList(testDepConf3, testDepConf4));
            put(testMasterConf2, WrapUtil.toList(ModuleDescriptor.DEFAULT_CONFIGURATION));
        }};
        assertEquals(expectedMapping, actualMapping);
    }

    @Test
    public void masterConfs() {
        String testDepConf1 = "a";
        String testMasterConf1 = "b";
        String testMasterConf2 = "c";
        mappingContainer.add(testDepConf1);
        mappingContainer.addMasters(testMasterConf1);
        mappingContainer.add(WrapUtil.toMap(testMasterConf2, WrapUtil.toList("depConf")));
        assertEquals(WrapUtil.toSet(testMasterConf1, testMasterConf2), mappingContainer.getMasterConfigurations());
    }

    @Test(expected= InvalidUserDataException.class)
    public void nullWildcardConf() {
        mappingContainer.add("a", null, "b");
    }

    @Test(expected= InvalidUserDataException.class)
    public void nullDefaultMasterConf() {
        mappingContainer.addMasters("a", null, "b");
    }

    @Test(expected= InvalidUserDataException.class)
    public void nullMasterConf() {
        mappingContainer.add(WrapUtil.<String, List<String>>toMap(null, WrapUtil.toList("a")));
    }

    @Test(expected= InvalidUserDataException.class)
    public void nullDepConf() {
        mappingContainer.add(WrapUtil.toMap("a", WrapUtil.toList("a", null)));
    }

    @Test(expected= InvalidUserDataException.class)
    public void nullConfList() {
        mappingContainer.add(WrapUtil.<String, List<String>>toMap("a", null));
    }
}
