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

import org.gradle.api.internal.resource.CachingResource;
import org.gradle.api.internal.resource.Resource;
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

    @Test
    public void cachesContentOfSource() {
        context.checking(new Expectations() {{
            one(delegate).getResource();
            will(returnValue(context.mock(Resource.class)));
        }});

        CachingScriptSource source = new CachingScriptSource(delegate);
        assertThat(source.getResource(), instanceOf(CachingResource.class));
        assertThat(source.getResource(), sameInstance(source.getResource()));
    }
}
