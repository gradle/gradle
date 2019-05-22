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
package org.gradle.groovy.scripts;

import static org.gradle.util.Matchers.*;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

import org.gradle.internal.resource.StringTextResource;
import org.junit.Test;

public class StringScriptSourceTest {
    private final StringScriptSource source = new StringScriptSource("<description>", "<content>");

    @Test
    public void usesProvidedContent() {
        assertThat(source.getResource(), instanceOf(StringTextResource.class));
        assertThat(source.getResource().getText(), equalTo("<content>"));
    }

    @Test
    public void generatesClassNameAndSourceFileNameUsingHashOfText() {
        assertThat(source.getClassName(), matchesRegexp("script_[a-z0-9]+"));
        assertThat(source.getFileName(), equalTo(source.getClassName()));
    }

    @Test
    public void sourcesWithDifferentTextHaveDifferentClassNames() {
        assertThat(source.getClassName(), equalTo(new StringScriptSource("?", "<content>").getClassName()));
        assertThat(source.getClassName(), not(equalTo(new StringScriptSource("?", "<other>").getClassName())));
    }

    @Test
    public void usesProvidedDescription() {
        assertThat(source.getDisplayName(), equalTo("<description>"));
    }
}
