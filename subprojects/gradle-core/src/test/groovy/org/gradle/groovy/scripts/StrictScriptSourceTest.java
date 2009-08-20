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
package org.gradle.groovy.scripts;

import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.integration.junit4.JMock;
import org.jmock.Expectations;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.gradle.util.GFileUtils;
import org.gradle.util.HelperUtil;
import org.gradle.api.GradleException;

import java.io.File;

@RunWith(JMock.class)
public class StrictScriptSourceTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final ScriptSource delegate = context.mock(ScriptSource.class);
    private final File sourceFile = new File(HelperUtil.makeNewTestDir(), "source");
    private final StrictScriptSource scriptSource = new StrictScriptSource(delegate);

    @Before
    public void setUp() {
        context.checking(new Expectations(){{
            allowing(delegate).getSourceFile();
            will(returnValue(sourceFile));

            allowing(delegate).getDisplayName();
            will(returnValue("<description>"));
        }});
    }

    @Test
    public void usesDelegateToFetchScriptTextWhenSourceFileExistss() {
        GFileUtils.writeStringToFile(sourceFile, "content");
        context.checking(new Expectations(){{
            one(delegate).getText();
            will(returnValue("text"));
        }});
        assertThat(scriptSource.getText(), equalTo("text"));
    }

    @Test
    public void cannotGetTextWhenSourceFileDoesNotExist() {
        try {
            scriptSource.getText();
            fail();
        } catch (GradleException e) {
            assertThat(e.getMessage(), equalTo("Cannot read <description> as it does not exist."));
        }
    }
    
    @Test
    public void cannotGetTextWhenSourceFileIsADirectory() {
        sourceFile.mkdirs();
        try {
            scriptSource.getText();
            fail();
        } catch (GradleException e) {
            assertThat(e.getMessage(), equalTo("Cannot read <description> as it is not a file."));
        }
    }
}
