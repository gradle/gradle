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
import org.gradle.api.initialization.Settings;
import org.gradle.api.internal.DynamicObjectAware;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.ThreadGlobalInstantiator;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.project.ServiceRegistryFactory;
import org.gradle.api.plugins.PluginContainer;
import org.gradle.configuration.ScriptPluginFactory;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.util.WrapUtil;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

@RunWith(org.jmock.integration.junit4.JMock.class)
public class SettingsFactoryTest {
    private JUnit4Mockery context = new JUnit4Mockery() {{
        setImposteriser(ClassImposteriser.INSTANCE);
    }};

    @Test
    public void createSettings() {
        final File expectedSettingsDir = new File("settingsDir");
        ScriptSource expectedScriptSource = context.mock(ScriptSource.class);
        Map<String, String> expectedGradleProperties = WrapUtil.toMap("key", "myvalue");
        StartParameter expectedStartParameter = new StartParameter();
        final ServiceRegistryFactory serviceRegistryFactory = context.mock(ServiceRegistryFactory.class);
        final SettingsInternalServiceRegistry settingsInternallServiceRegistry = context.mock(SettingsInternalServiceRegistry.class);
        final PluginContainer pluginContainer = context.mock(PluginContainer.class);
        final FileResolver fileResolver = context.mock(FileResolver.class);
        final ScriptPluginFactory scriptPluginFactory = context.mock(ScriptPluginFactory.class);

        final ProjectDescriptorRegistry expectedProjectDescriptorRegistry = context.mock(ProjectDescriptorRegistry.class);

        context.checking(new Expectations() {{
            one(serviceRegistryFactory).createFor(with(any(Settings.class)));
            will(returnValue(settingsInternallServiceRegistry));
            one(settingsInternallServiceRegistry).get(PluginContainer.class);
            will(returnValue(pluginContainer));
            one(settingsInternallServiceRegistry).get(FileResolver.class);
            will(returnValue(fileResolver));
            one(settingsInternallServiceRegistry).get(ScriptPluginFactory.class);
            will(returnValue(scriptPluginFactory));
            one(settingsInternallServiceRegistry).get(ProjectDescriptorRegistry.class);
            will(returnValue(expectedProjectDescriptorRegistry));
            one(expectedProjectDescriptorRegistry).addProject(with(any(DefaultProjectDescriptor.class)));
        }});


        SettingsFactory settingsFactory = new SettingsFactory(ThreadGlobalInstantiator.getOrCreate(), serviceRegistryFactory);
        final URLClassLoader urlClassLoader = new URLClassLoader(new URL[0]);
        GradleInternal gradle = context.mock(GradleInternal.class);

        DefaultSettings settings = (DefaultSettings) settingsFactory.createSettings(gradle,
                expectedSettingsDir, expectedScriptSource, expectedGradleProperties, expectedStartParameter, urlClassLoader);

        assertSame(gradle, settings.getGradle());
        assertSame(expectedProjectDescriptorRegistry, settings.getProjectDescriptorRegistry());
        for (Map.Entry<String, String> entry : expectedGradleProperties.entrySet()) {
            assertEquals(entry.getValue(), ((DynamicObjectAware) settings).getAsDynamicObject().getProperty(entry.getKey()));
        }

        assertSame(expectedSettingsDir, settings.getSettingsDir());
        assertSame(expectedScriptSource, settings.getSettingsScript());
        assertSame(expectedStartParameter, settings.getStartParameter());
    }
}
