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
package org.gradle.api.tasks.mirah;

import org.gradle.api.internal.AbstractTask;
import org.gradle.api.tasks.AbstractTaskTest;
import org.gradle.util.WrapUtil;
import org.jmock.integration.junit4.JMock;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.gradle.api.tasks.compile.AbstractCompileTest.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

@RunWith(JMock.class)
public class ScalaDocTest extends AbstractTaskTest {
    private ScalaDoc mirahDoc;

    @Override
    public AbstractTask getTask() {
        return mirahDoc;
    }

    @Before
    public void setUp() {
        mirahDoc = createTask(ScalaDoc.class);
    }

    @Test
    public void testScalaIncludes() {
        assertSame(mirahDoc.include(TEST_PATTERN_1, TEST_PATTERN_2), mirahDoc);
        assertEquals(mirahDoc.getIncludes(), WrapUtil.toLinkedSet(TEST_PATTERN_1, TEST_PATTERN_2));

        assertSame(mirahDoc.include(TEST_PATTERN_3), mirahDoc);
        assertEquals(mirahDoc.getIncludes(), WrapUtil.toLinkedSet(TEST_PATTERN_1, TEST_PATTERN_2, TEST_PATTERN_3));
    }

    @Test
    public void testScalaExcludes() {
        assertSame(mirahDoc.exclude(TEST_PATTERN_1, TEST_PATTERN_2), mirahDoc);
        assertEquals(mirahDoc.getExcludes(), WrapUtil.toLinkedSet(TEST_PATTERN_1, TEST_PATTERN_2));

        assertSame(mirahDoc.exclude(TEST_PATTERN_3), mirahDoc);
        assertEquals(mirahDoc.getExcludes(), WrapUtil.toLinkedSet(TEST_PATTERN_1, TEST_PATTERN_2, TEST_PATTERN_3));
    }
}
