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
import org.gradle.api.internal.GradleInternal;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.util.WrapUtil;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.Map;
import java.net.URLClassLoader;
import java.net.URL;

/**
 * @author Hans Dockter
 */
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
        IProjectDescriptorRegistry expectedProjectDescriptorRegistry = new DefaultProjectDescriptorRegistry();
        StartParameter expectedStartParameter = new StartParameter();
        SettingsFactory settingsFactory = new SettingsFactory(expectedProjectDescriptorRegistry);
        final URLClassLoader urlClassLoader = new URLClassLoader(new URL[0]);
        GradleInternal gradle = context.mock(GradleInternal.class);

        DefaultSettings settings = (DefaultSettings) settingsFactory.createSettings(gradle,
                expectedSettingsDir, expectedScriptSource, expectedGradleProperties, expectedStartParameter, urlClassLoader);

        assertSame(gradle, settings.getGradle());
        assertSame(expectedProjectDescriptorRegistry, settings.getProjectDescriptorRegistry());
        assertEquals(expectedGradleProperties, settings.getAdditionalProperties());
        assertSame(expectedSettingsDir, settings.getSettingsDir());
        assertSame(expectedScriptSource, settings.getSettingsScript());
        assertSame(expectedStartParameter, settings.getStartParameter());
    }
}
