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

import org.gradle.api.dependencies.ExcludeRuleContainer;
import org.gradle.api.dependencies.ModuleDependency;
import org.gradle.util.WrapUtil;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Set;

/**
 * @author Hans Dockter
 */
@RunWith(org.jmock.integration.junit4.JMock.class)
public class ModuleDependencyFactoryTest {
    private ModuleDependencyFactory moduleDependencyFactory;

    private JUnit4Mockery context = new JUnit4Mockery();    

    @Test
    public void testCreateDependency() {
        final IExcludeRuleContainerFactory excludeRuleContainerFactoryMock = context.mock(IExcludeRuleContainerFactory.class);
        final ExcludeRuleContainer expectedExcludeRuleContainer = new DefaultExcludeRuleContainer();
        context.checking(new Expectations() {{
          one(excludeRuleContainerFactoryMock).createExcludeRuleContainer(); will(returnValue(expectedExcludeRuleContainer));
        }});
        Set<String> expectedConfs = WrapUtil.toSet("conf1");
        String expectedDescription = "junit:junit:4.0";
        moduleDependencyFactory = new ModuleDependencyFactory(excludeRuleContainerFactoryMock);
        ModuleDependency moduleDependency = (ModuleDependency)
                moduleDependencyFactory.createDependency(expectedConfs, expectedDescription, null);
        assertEquals(expectedConfs, moduleDependency.getConfs());
        assertEquals(expectedDescription, moduleDependency.getUserDependencyDescription());
        assertSame(expectedExcludeRuleContainer, moduleDependency.getExcludeRules());
    }

}
