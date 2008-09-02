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
package org.gradle.initialization;

import org.gradle.StartParameter;
import org.gradle.api.DependencyManager;
import org.gradle.api.Project;
import org.gradle.api.internal.dependencies.DependencyManagerFactory;
import org.gradle.util.HelperUtil;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import static org.junit.Assert.assertSame;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Hans Dockter
 */
@RunWith(org.jmock.integration.junit4.JMock.class)
public class SettingsFactoryTest {
    private JUnit4Mockery context = new JUnit4Mockery();

    @Test
    public void createSettings() {
        final DependencyManagerFactory dependencyManagerFactoryMock = context.mock(DependencyManagerFactory.class);
        final DependencyManager dependencyManagerMock = context.mock(DependencyManager.class);
        context.checking(new Expectations() {{
          allowing(dependencyManagerFactoryMock).createDependencyManager(with(any(Project.class))); will(returnValue(dependencyManagerMock));
          allowing(dependencyManagerMock).addConfiguration(with(any(String.class)));
        }});
        BuildSourceBuilder expectedBuildSourceBuilder = new BuildSourceBuilder(new EmbeddedBuildExecuter());
        ISettingsFinder expectedSettingsFinder = new ParentDirSettingsFinder();
        StartParameter expectedStartParameter = HelperUtil.dummyStartParameter();

        SettingsFactory settingsFactory = new SettingsFactory();
        DefaultSettings settings = (DefaultSettings) settingsFactory.createSettings(dependencyManagerFactoryMock, expectedBuildSourceBuilder, expectedSettingsFinder, expectedStartParameter);
        assertSame(dependencyManagerMock, settings.getDependencyManager());
        assertSame(expectedBuildSourceBuilder, settings.getBuildSourceBuilder());
        assertSame(expectedSettingsFinder, settings.getRootFinder());
        assertSame(expectedStartParameter, settings.getStartParameter());
    }
}
