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

import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

@RunWith(JMock.class)
public class CachingScriptSourceTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final ScriptSource delegate = context.mock(ScriptSource.class);
    private final CachingScriptSource source = new CachingScriptSource(delegate);

    @Test
    public void cachesContentOfSource() {
        context.checking(new Expectations() {{
            one(delegate).getText();
            will(returnValue("content"));
        }});

        assertThat(source.getText(), equalTo("content"));
        assertThat(source.getText(), equalTo("content"));
    }
}
