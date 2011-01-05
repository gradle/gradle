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

import org.gradle.util.GFileUtils;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.Expectations;
import org.gradle.StartParameter;
import org.gradle.api.internal.GradleInternal;
import org.gradle.groovy.scripts.ScriptSource;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.hamcrest.Matchers.*;

import java.util.List;
import java.util.ArrayList;
import java.io.File;

public class DefaultInitScriptFinderTest {
    @Test
    public void testFindScripts() {
        JUnit4Mockery context = new JUnit4Mockery();

        final GradleInternal gradleMock = context.mock(GradleInternal.class);
        final StartParameter testStartParameter = new StartParameter();
        testStartParameter.addInitScript(new File("some init script"));
        testStartParameter.addInitScript(new File("/path/to/another init script"));

        context.checking(new Expectations() {{
            allowing(gradleMock).getStartParameter();
            will(returnValue(testStartParameter));
        }});

        List<ScriptSource> sourceList = new DefaultInitScriptFinder().findScripts(gradleMock);
        assertThat(getSourceFiles(sourceList), equalTo(canonicalise(testStartParameter.getInitScripts())));
    }

    private List<File> canonicalise(List<File> files) {
        List<File> results = new ArrayList<File>();
        for (File file : files) {
            results.add(GFileUtils.canonicalise(file));
        }
        return results;
    }

    private List<File> getSourceFiles(List<ScriptSource> sources) {
        List<File> results = new ArrayList<File>(sources.size());
        for (ScriptSource source : sources) {
            results.add(source.getResource().getFile());
        }
        return results;
    }
}
