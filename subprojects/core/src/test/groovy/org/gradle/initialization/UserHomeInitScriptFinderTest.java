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

import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

@RunWith(JMock.class)
public class UserHomeInitScriptFinderTest {
    @Rule
    public final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider();
    private final JUnit4Mockery context = new JUnit4Mockery();
    private UserHomeInitScriptFinder finder;

    @Before
    public void setUp() throws Exception {
        finder = new UserHomeInitScriptFinder(tmpDir.getTestDirectory());
    }

    @Test
    public void addsUserInitScriptWhenItExists() {
        File initScript = tmpDir.createFile("init.gradle");

        List<File> sourceList = new ArrayList<File>();
        finder.findScripts(sourceList);
        assertThat(sourceList.size(), equalTo(1));
        assertThat(sourceList.get(0), equalTo(initScript));
    }

    @Test
    public void doesNotAddUserInitScriptsWhenTheyDoNotExist() {
        List<File> sourceList = new ArrayList<File>();
        finder.findScripts(sourceList);
        assertThat(sourceList.size(), equalTo(0));
    }

    @Test
    public void addsInitScriptsFromInitDirectoryWhenItExists() {
        File initScript = tmpDir.createFile("init.d/script.gradle");

        List<File> sourceList = new ArrayList<File>();
        finder.findScripts(sourceList);
        assertThat(sourceList.size(), equalTo(1));
        assertThat(sourceList.get(0), equalTo(initScript));
    }
}
