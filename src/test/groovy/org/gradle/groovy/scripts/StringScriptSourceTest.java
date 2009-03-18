/*
 * Copyright 2008 the original author or authors.
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

import org.gradle.api.Project;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import org.junit.Test;

public class StringScriptSourceTest {
    private final StringScriptSource source = new StringScriptSource("<description>", "<content>");

    @Test
    public void usesProvidedContent() {
        assertThat(source.getText(), equalTo("<content>"));
    }

    @Test
    public void hasNoContentWhenScriptContentIsEmpty() {
        StringScriptSource source = new StringScriptSource("<description>", "");
        assertThat(source.getText(), nullValue());
    }

    @Test
    public void hasNoSourceFile() {
        assertThat(source.getSourceFile(), nullValue());
    }

    @Test
    public void hasHardcodedClassName() {
        assertThat(source.getClassName(), equalTo(Project.EMBEDDED_SCRIPT_ID));
    }

    @Test
    public void usesProvidedDescription() {
        assertThat(source.getDisplayName(), equalTo("<description>"));
    }
}
