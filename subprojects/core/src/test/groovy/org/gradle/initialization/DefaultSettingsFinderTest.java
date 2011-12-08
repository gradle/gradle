/*
 * Copyright 2010 the original author or authors.
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
import org.gradle.groovy.scripts.StringScriptSource;
import org.gradle.initialization.layout.BuildLayout;
import org.gradle.initialization.layout.BuildLayoutFactory;
import org.gradle.util.GFileUtils;
import org.gradle.util.JUnit4GroovyMockery;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

/**
 * @author Hans Dockter
 */
@RunWith(org.jmock.integration.junit4.JMock.class)
public class DefaultSettingsFinderTest {

    private JUnit4Mockery context = new JUnit4GroovyMockery();
    private BuildLayoutFactory layoutFactory = context.mock(BuildLayoutFactory.class);
    private DefaultSettingsFinder defaultSettingsFinder = new DefaultSettingsFinder(layoutFactory);

    private final StartParameter startParameter = new StartParameter();
    private File settingsFile = new File("parent", "testFile1");
    private File settingsDir = new File("settings");

    @Test
    public void testSettingsFileFound() {
        context.checking(new Expectations() {{
            one(layoutFactory).getLayoutFor(startParameter.getCurrentDir(), startParameter.isSearchUpwards());
            will(returnValue(new BuildLayout(settingsDir, settingsDir, settingsFile)));
        }});
        SettingsLocation settingsLocation = defaultSettingsFinder.find(startParameter);
        assertEquals(settingsDir, settingsLocation.getSettingsDir());
        assertEquals(GFileUtils.canonicalise(settingsFile), settingsLocation.getSettingsScriptSource().getResource().getFile());
    }

    @Test
    public void testNotFound() {
        context.checking(new Expectations() {{
            one(layoutFactory).getLayoutFor(startParameter.getCurrentDir(), startParameter.isSearchUpwards());
            will(returnValue(new BuildLayout(settingsDir, settingsDir, null)));
        }});
        SettingsLocation settingsLocation = defaultSettingsFinder.find(startParameter);
        assertEquals(settingsDir, settingsLocation.getSettingsDir());
        assertThat(settingsLocation.getSettingsScriptSource(), instanceOf(StringScriptSource.class));
        assertThat(settingsLocation.getSettingsScriptSource().getResource().getText(), equalTo(""));
    }
}