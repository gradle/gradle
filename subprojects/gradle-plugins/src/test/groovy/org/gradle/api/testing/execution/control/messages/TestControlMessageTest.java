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
package org.gradle.api.testing.execution.control.messages;

import org.apache.commons.io.IOUtils;
import org.junit.Test;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.*;

/**
 * @author Tom Eyckmans
 */
public abstract class TestControlMessageTest<T extends TestControlMessage> {

    protected abstract T createMessageObject(final int senderId);

    protected abstract void assertTestControlMessage(T originalMessage, T deserializedMessage);

    @Test
    public void serializationTest() throws IOException, ClassNotFoundException
    {
        final T originalMessage = createMessageObject(1);

        final byte[] objectBytes = serialize(originalMessage);

        assertNotNull(objectBytes);
        assertTrue(objectBytes.length > 0);

        final T deserializedMessage = deserialize(objectBytes);

        assertTestControlMessage(originalMessage, deserializedMessage);
    }

    protected byte[] serialize(Object obj) throws IOException
    {
        byte[] serializedBytes = null;

        ByteArrayOutputStream out = null;
        ObjectOutputStream objOut = null;

        try {
            out = new ByteArrayOutputStream();
            objOut = new ObjectOutputStream(out);

            objOut.writeObject(obj);
            objOut.flush();
            out.flush();

            serializedBytes = out.toByteArray();
        }
        finally {
            IOUtils.closeQuietly(objOut);
            IOUtils.closeQuietly(out);
        }

        return serializedBytes;
    }

    protected T deserialize(byte[] serialBytes) throws IOException, ClassNotFoundException
    {
        T deserializedObject = null;

        ByteArrayInputStream in = null;
        ObjectInputStream objIn = null;

        try {
            in = new ByteArrayInputStream(serialBytes);
            objIn = new ObjectInputStream(in);

            deserializedObject = (T)objIn.readObject();
        }
        finally {
            IOUtils.closeQuietly(objIn);
            IOUtils.closeQuietly(in);
        }

        return deserializedObject;
    }
}
