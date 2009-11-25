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
package org.gradle.api.testing.execution.control.messages.client;

import org.junit.Test;
import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;
import org.gradle.api.testing.execution.control.messages.TestControlMessageTest;

/**
 * @author Tom Eyckmans
 */
public abstract class AbstractTestClientControlMessageTest<T extends AbstractTestClientControlMessage> extends TestControlMessageTest<T> {

    @Test(expected = IllegalArgumentException.class )
    public void negativeForkIdConstructorTest()
    {
        createMessageObject(-1);

        fail();
    }

    @Test (expected = IllegalArgumentException.class )
    public void zeroForkIdConstructorTest()
    {
        createMessageObject(0);

        fail();
    }

    @Test
    public void okForkIdConstructorTest()
    {
        final int forkId = 1;

        final T messageObject = createMessageObject(forkId);

        assertNotNull(messageObject);
        assertEquals(forkId, messageObject.getForkId());
    }

    protected void assertTestControlMessage(T originalMessage, T deserializedMessage)
    {
        assertEquals(originalMessage.getForkId(), deserializedMessage.getForkId());
        assertTestClientControlMessage(originalMessage, deserializedMessage);
    }

    protected abstract void assertTestClientControlMessage(T originalMessage, T deserializedMessage);
}
