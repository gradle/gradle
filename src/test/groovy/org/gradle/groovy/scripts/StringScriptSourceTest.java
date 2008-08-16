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

import org.junit.Test;
import org.junit.Before;
import static org.junit.Assert.*;
import static org.hamcrest.Matchers.*;
import org.junit.runner.RunWith;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.jmock.Expectations;
import org.gradle.api.internal.project.ImportsReader;
import org.gradle.util.HelperUtil;

import java.io.File;

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
        assertThat(source.getClassName(), equalTo("script"));
    }

    @Test
    public void usesProvidedDescription() {
        assertThat(source.getDescription(), equalTo("<description>"));
    }
}
