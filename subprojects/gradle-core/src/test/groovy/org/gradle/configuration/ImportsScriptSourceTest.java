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
package org.gradle.configuration;

import org.gradle.api.internal.resource.Resource;
import org.gradle.groovy.scripts.ScriptSource;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

@RunWith(org.jmock.integration.junit4.JMock.class)
public class ImportsScriptSourceTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private ScriptSource backingSource;
    private ImportsReader importsReader;
    private ImportsScriptSource source;
    private Resource resource;

    @Before
    public void setUp() {
        context.setImposteriser(ClassImposteriser.INSTANCE);
        backingSource = context.mock(ScriptSource.class);
        importsReader = context.mock(ImportsReader.class);
        resource = context.mock(Resource.class);
        source = new ImportsScriptSource(backingSource, importsReader);
    }
    
    @Test
    public void prependsImportsToScriptText() {
        context.checking(new Expectations() {{
            one(backingSource).getResource();
            will(returnValue(resource));

            one(resource).getText();
            will(returnValue("<content>"));

            one(importsReader).getImports();
            will(returnValue("<imports>"));
        }});

        assertThat(source.getResource().getText(), equalTo("<content>\n<imports>"));
    }

    @Test
    public void doesNotPrependImportsWhenScriptHasNoText() {
        context.checking(new Expectations(){{
            one(backingSource).getResource();
            will(returnValue(resource));

            one(resource).getText();
            will(returnValue(""));
        }});

        assertThat(source.getResource().getText(), equalTo(""));
    }

    @Test
    public void delegatesAllOtherMethodsToBackingScriptSource() {
        context.checking(new Expectations(){{
            one(backingSource).getClassName();
            will(returnValue("classname"));

            one(backingSource).getDisplayName();
            will(returnValue("description"));
        }});

        assertThat(source.getClassName(), equalTo("classname"));
        assertThat(source.getDisplayName(), equalTo("description"));
    }
}
