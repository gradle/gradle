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

import org.junit.Test;
import static org.junit.Assert.assertThat;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.Expectations;
import org.gradle.StartParameter;
import org.gradle.api.internal.GradleInternal;
import org.gradle.groovy.scripts.ScriptSource;
import static org.hamcrest.Matchers.equalTo;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;

public class UserHomeInitScriptFinderTest {
    @Test
    public void testFindScripts() throws IOException {
        JUnit4Mockery context = new JUnit4Mockery();

        final GradleInternal gradleMock = context.mock(GradleInternal.class);
        final InitScriptFinder initScriptFinderMock = context.mock(InitScriptFinder.class);
        final StartParameter testStartParameter = new StartParameter();
        final File homeDir = new File("gradle home dir").getCanonicalFile();
        testStartParameter.setGradleUserHomeDir(homeDir);

        context.checking(new Expectations() {{
            allowing(initScriptFinderMock).findScripts(gradleMock);
            will(returnValue(new ArrayList()));
            allowing(gradleMock).getStartParameter();
            will(returnValue(testStartParameter));
        }});

        List<ScriptSource> sourceList = new UserHomeInitScriptFinder(initScriptFinderMock).findScripts(gradleMock);
        assertThat(sourceList.size(), equalTo(1));
        assertThat(sourceList.get(0).getSourceFile(), equalTo(new File(homeDir, "init.gradle")));
    }
}
