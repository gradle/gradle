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
package org.gradle.api.testing.execution.control.messages.server;

import org.gradle.api.testing.execution.control.messages.TestControlMessageTest;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;

/**
 * @author Tom Eyckmans
 */
public abstract class AbstractTestServerControlMessageTest<T extends AbstractTestServerControlMessage> extends TestControlMessageTest<T> {

    @Test(expected = IllegalArgumentException.class)
    public void negativePipelineIdConstructorTest()
    {
        createMessageObject(-1);

        fail();
    }

    @Test (expected = IllegalArgumentException.class)
    public void zeroPipelineIdConstructorTest()
    {
        createMessageObject(0);

        fail();
    }

    @Test
    public void okPipelineIdConstructorTest()
    {
        final int pipelineId = 1;

        final T messageObject = createMessageObject(pipelineId);

        assertNotNull(messageObject);
        assertEquals(pipelineId, messageObject.getPipelineId());
    }

    protected void assertTestControlMessage(T originalMessage, T deserializedMessage) {
        assertEquals(originalMessage.getPipelineId(), deserializedMessage.getPipelineId());

        assertTestServerControlMessage(originalMessage, deserializedMessage);
    }

    protected abstract void assertTestServerControlMessage(T originalMessage, T deserializedMessage);
}
