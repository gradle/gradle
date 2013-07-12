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
package org.gradle.api.internal.file.copy;

import org.gradle.api.internal.file.FileResolver;
import org.gradle.internal.reflect.DirectInstantiator;
import org.gradle.internal.reflect.Instantiator;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

@RunWith(JMock.class)
public class FileCopyActionImplTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final FileResolver fileResolver = context.mock(FileResolver.class);
    private final Instantiator instantiator = new DirectInstantiator();
    private final FileCopyActionImpl spec = new FileCopyActionImpl(instantiator, fileResolver, context.mock(CopySpecContentVisitor.class));

    @Test public void testRootSpecResolvesItsIntoArgAsDestinationDir() {
        final File file = new File("base dir");

        spec.into("somedir");

        context.checking(new Expectations() {{
            allowing(fileResolver).resolve("somedir");
            will(returnValue(file));
        }});

        assertThat(spec.getDestinationDir(), equalTo(file));
    }

    @Test public void testRootSpecHasNoDefaultDestinationDir() {
        assertThat(spec.getDestinationDir(), nullValue());
    }

}
