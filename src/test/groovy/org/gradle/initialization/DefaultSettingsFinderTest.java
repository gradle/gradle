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

import org.gradle.util.WrapUtil;
import org.gradle.util.HelperUtil;
import org.gradle.util.Matchers;
import org.gradle.StartParameter;
import org.gradle.groovy.scripts.StringScriptSource;
import org.gradle.groovy.scripts.ScriptSource;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.Expectations;

import java.io.File;

/**
 * @author Hans Dockter
 */
@RunWith(org.jmock.integration.junit4.JMock.class)
public class DefaultSettingsFinderTest {
    private static final StartParameter TEST_START_PARAMETER = HelperUtil.dummyStartParameter();
    private static final File TEST_SETTINGSFILE = new File("parent", "testFile1");
    private DefaultSettingsFinder defaultSettingsFinder;
    private ISettingsFileSearchStrategy searchStrategyMock1;
    private ISettingsFileSearchStrategy searchStrategyMock2;

    private JUnit4Mockery context = new JUnit4Mockery();

    @Before
    public void setUp() {
        searchStrategyMock1 = context.mock(ISettingsFileSearchStrategy.class, "strategy1");
        searchStrategyMock2 = context.mock(ISettingsFileSearchStrategy.class, "strategy2");
        defaultSettingsFinder = new DefaultSettingsFinder(WrapUtil.toList(searchStrategyMock1, searchStrategyMock2));
    }

    @Test
    public void testFindWithStrategy1() {
        context.checking(new Expectations() {{
            allowing(searchStrategyMock1).find(TEST_START_PARAMETER);
            will(returnValue(TEST_SETTINGSFILE));
        }});
        SettingsLocation settingsLocation = defaultSettingsFinder.find(TEST_START_PARAMETER);
        assertEquals(TEST_SETTINGSFILE.getParentFile(), settingsLocation.getSettingsDir());
        assertEquals(TEST_SETTINGSFILE, settingsLocation.getSettingsScriptSource().getSourceFile());
    }

    @Test
    public void testFindWithStrategy2() {
        context.checking(new Expectations() {{
            allowing(searchStrategyMock1).find(TEST_START_PARAMETER);
            will(returnValue(null));
            allowing(searchStrategyMock2).find(TEST_START_PARAMETER);
            will(returnValue(TEST_SETTINGSFILE));
        }});
        SettingsLocation settingsLocation = defaultSettingsFinder.find(TEST_START_PARAMETER);
        assertEquals(TEST_SETTINGSFILE.getParentFile(), settingsLocation.getSettingsDir());
        assertEquals(TEST_SETTINGSFILE, settingsLocation.getSettingsScriptSource().getSourceFile());
    }

    @Test
    public void testNotFound() {
        context.checking(new Expectations() {{
            allowing(searchStrategyMock1).find(TEST_START_PARAMETER);
            will(returnValue(null));
            allowing(searchStrategyMock2).find(TEST_START_PARAMETER);
            will(returnValue(null));
        }});
        SettingsLocation settingsLocation = defaultSettingsFinder.find(TEST_START_PARAMETER);
        assertEquals(TEST_START_PARAMETER.getCurrentDir(), settingsLocation.getSettingsDir());
        ScriptSource expectedSettingsSource = new StringScriptSource("empty settings file", "");
        assertThat(settingsLocation.getSettingsScriptSource(), Matchers.reflectionEquals(
                expectedSettingsSource));
    }
}