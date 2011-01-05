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

import org.gradle.api.Transformer;
import org.gradle.api.file.FileCopyDetails;
import org.gradle.api.file.RelativePath;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JMock.class)
public class RenamingCopyActionTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final Transformer<String> transformer = context.mock(Transformer.class);
    private final FileCopyDetails details = context.mock(FileCopyDetails.class);
    private final RenamingCopyAction action = new RenamingCopyAction(transformer);

    @Test
    public void transformsLastSegmentOfPath() {
        context.checking(new Expectations() {{
            allowing(details).getRelativePath();
            will(returnValue(new RelativePath(true, "a", "b")));
            one(transformer).transform("b");
            will(returnValue("c"));
            one(details).setRelativePath(new RelativePath(true, "a", "c"));
        }});

        action.execute(details);
    }
}
