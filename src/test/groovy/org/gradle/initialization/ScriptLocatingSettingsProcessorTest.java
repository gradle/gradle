/*
 * Copyright 2007, 2008 the original author or authors.
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
import org.gradle.util.WrapUtil;
import static org.gradle.util.WrapUtil.*;
import org.gradle.api.initialization.ProjectDescriptor;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.internal.project.IProjectRegistry;
import org.gradle.api.internal.project.ProjectIdentifier;
import static org.hamcrest.Matchers.*;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

@RunWith(JMock.class)
public class ScriptLocatingSettingsProcessorTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final SettingsProcessor delegate = context.mock(SettingsProcessor.class);
    private final ISettingsFinder finder = context.mock(ISettingsFinder.class);
    private StartParameter startParameter;
    private final IGradlePropertiesLoader propertiesLoader = context.mock(IGradlePropertiesLoader.class);
    private final SettingsInternal settings = context.mock(SettingsInternal.class, "settings");
    private final SettingsProcessor processor = new ScriptLocatingSettingsProcessor(delegate);
    private final File currentDir = new File("currentDir");
    private final File settingsDir = new File("settingsDir");
    private final IProjectRegistry<ProjectIdentifier> projectRegistry = context.mock(IProjectRegistry.class);
    private final ProjectSpec defaultProjectSelector = context.mock(ProjectSpec.class);

    @Before
    public void setUp() {
        context.setImposteriser(ClassImposteriser.INSTANCE);
        startParameter = context.mock(StartParameter.class);

        context.checking(new Expectations(){{
            allowing(startParameter).getDefaultProjectSelector();
            will(returnValue(defaultProjectSelector));

            allowing(settings).getProjectRegistry();
            will(returnValue(projectRegistry));
        }});
    }

    @Test
    public void usesDelegateToCreateSettings() {
        context.checking(new Expectations() {{
            one(finder).find(startParameter);

            one(finder).getSettingsDir();
            will(returnValue(settingsDir));

            one(propertiesLoader).loadProperties(settingsDir, startParameter);

            one(delegate).process(finder, startParameter, propertiesLoader);
            will(returnValue(settings));

            one(projectRegistry).findAll(defaultProjectSelector);
            will(returnValue(toSet(context.mock(ProjectDescriptor.class))));
        }});

        assertThat(processor.process(finder, startParameter, propertiesLoader), sameInstance(settings));
    }

    @Test
    public void usesCurrentDirAsSettingsDirWhenLocatedSettingsDoNotContainProjectForCurrentDir() {
        final StartParameter noSearchParameter = new StartParameter();
        final SettingsInternal currentDirSettings = context.mock(SettingsInternal.class, "currentDirSettings");

        context.checking(new Expectations() {{
            one(finder).find(startParameter);

            one(finder).getSettingsDir();
            will(returnValue(settingsDir));

            one(propertiesLoader).loadProperties(settingsDir, startParameter);

            one(delegate).process(finder, startParameter, propertiesLoader);
            will(returnValue(settings));

            one(projectRegistry).findAll(defaultProjectSelector);
            will(returnValue(toSet()));

            one(startParameter).newInstance();
            will(returnValue(noSearchParameter));

            one(finder).find(noSearchParameter);

            one(finder).getSettingsDir();
            will(returnValue(currentDir));

            one(propertiesLoader).loadProperties(currentDir, noSearchParameter);

            one(delegate).process(finder, noSearchParameter, propertiesLoader);
            will(returnValue(currentDirSettings));
        }});

        assertThat(processor.process(finder, startParameter, propertiesLoader), sameInstance(currentDirSettings));
    }
}
