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
package org.gradle.api.internal.file;

import org.gradle.api.Transformer;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.RelativePath;
import org.gradle.util.HelperUtil;
import static org.gradle.util.WrapUtil.*;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.hamcrest.Matchers.*;
import groovy.lang.Closure;

@RunWith(JMock.class)
public class DefaultCopyDestinationMapperTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final FileTreeElement element = context.mock(FileTreeElement.class);
    private final DefaultCopyDestinationMapper mapper = new DefaultCopyDestinationMapper();

    @Test
    public void returnsTheRelativePathWhenThereAreNoTransformers() {
        RelativePath path = expectPath(new RelativePath(true, "a", "b"));

        assertThat(mapper.getPath(element), equalTo(path));
    }
    
    @Test
    public void passesTheNameToEachNameTransformer() {
        final Transformer<String> transformer1 = context.mock(Transformer.class, "transformer1");
        final Transformer<String> transformer2 = context.mock(Transformer.class, "transformer2");

        mapper.add(transformer1);
        mapper.add(transformer2);

        context.checking(new Expectations() {{
            one(transformer1).transform("a");
            will(returnValue("b"));
            one(transformer2).transform("b");
            will(returnValue("c"));
        }});

        expectPath(new RelativePath(true, "a"));
        RelativePath mapped = mapper.getPath(element);
        assertTrue(mapped.isFile());
        assertArrayEquals(toArray("c"), mapped.getSegments());
    }

    @Test
    public void passesTheNameToEachClosure() {
        Closure cl = HelperUtil.toClosure("{ s -> \"[$s]\" }");

        mapper.add(cl);
        mapper.add(cl);

        expectPath(new RelativePath(true, "a"));
        RelativePath mapped = mapper.getPath(element);
        assertTrue(mapped.isFile());
        assertArrayEquals(toArray("[[a]]"), mapped.getSegments());
    }

    private RelativePath expectPath(final RelativePath path) {
        context.checking(new Expectations(){{
            allowing(element).getRelativePath();
            will(returnValue(path));
        }});
        return path;
    }
}
