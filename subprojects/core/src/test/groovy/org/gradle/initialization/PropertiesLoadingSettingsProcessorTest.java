/*
 * Copyright 2009 the original author or authors.
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
import org.gradle.api.internal.SettingsInternal;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;

import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertThat;

@RunWith(JMock.class)
public class PropertiesLoadingSettingsProcessorTest {
    private final JUnit4Mockery context = new JUnit4Mockery();

    @Test
    public void loadsPropertiesThenDelegatesToBackingSettingsProcessor() {
        final SettingsProcessor delegate = context.mock(SettingsProcessor.class);
        final URLClassLoader urlClassLoader = new URLClassLoader(new URL[0]);
        final IGradlePropertiesLoader propertiesLoader = context.mock(IGradlePropertiesLoader.class);
        final StartParameter startParameter = new StartParameter();
        final SettingsInternal settings = context.mock(SettingsInternal.class);
        final File settingsDir = new File("root");
        final GradleInternal gradle = context.mock(GradleInternal.class);
        final SettingsLocation settingsLocation = new SettingsLocation(settingsDir, new File("foo"));

        PropertiesLoadingSettingsProcessor processor = new PropertiesLoadingSettingsProcessor(delegate, propertiesLoader);

        context.checking(new Expectations() {{
            one(propertiesLoader).loadProperties(settingsDir);
            one(delegate).process(gradle, settingsLocation, urlClassLoader, startParameter);
            will(returnValue(settings));
        }});

        assertThat(processor.process(gradle, settingsLocation, urlClassLoader, startParameter), sameInstance(settings));
    }
}
