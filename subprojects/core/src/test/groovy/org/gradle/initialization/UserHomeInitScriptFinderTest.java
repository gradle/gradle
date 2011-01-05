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
import org.gradle.api.internal.GradleInternal;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.groovy.scripts.UriScriptSource;
import org.gradle.util.TemporaryFolder;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

public class UserHomeInitScriptFinderTest {
    @Rule
    public final TemporaryFolder tmpDir = new TemporaryFolder();
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final GradleInternal gradleMock = context.mock(GradleInternal.class);
    private final InitScriptFinder initScriptFinderMock = context.mock(InitScriptFinder.class);
    private final StartParameter testStartParameter = new StartParameter();

    @Before
    public void setup() {
        testStartParameter.setGradleUserHomeDir(tmpDir.getDir());
        context.checking(new Expectations() {{
            allowing(gradleMock).getStartParameter();
            will(returnValue(testStartParameter));
        }});
    }

    @Test
    public void addsUserInitScriptWhenItExists() throws IOException {
        File initScript = tmpDir.file("init.gradle").createFile();

        context.checking(new Expectations() {{
            allowing(initScriptFinderMock).findScripts(gradleMock);
            will(returnValue(new ArrayList()));
        }});

        List<ScriptSource> sourceList = new UserHomeInitScriptFinder(initScriptFinderMock).findScripts(gradleMock);
        assertThat(sourceList.size(), equalTo(1));
        assertThat(sourceList.get(0), instanceOf(UriScriptSource.class));
        assertThat(sourceList.get(0).getResource().getFile(), equalTo(initScript));
    }

    @Test
    public void doesNotAddUserInitScriptWhenItDoesNotExist() throws IOException {
        context.checking(new Expectations() {{
            allowing(initScriptFinderMock).findScripts(gradleMock);
            will(returnValue(new ArrayList()));
        }});

        List<ScriptSource> sourceList = new UserHomeInitScriptFinder(initScriptFinderMock).findScripts(gradleMock);
        assertThat(sourceList.size(), equalTo(0));
    }
}
