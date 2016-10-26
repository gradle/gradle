/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.test.fixtures;

import org.gradle.internal.UncheckedException;
import org.gradle.internal.io.ClassLoaderObjectInputStream;
import org.gradle.internal.io.NullOutputStream;
import org.gradle.internal.io.StreamByteBuffer;

import java.io.ObjectOutputStream;

public class SerializationFixture {
    public static void assertSerializable(Object obj) {
        try {
            ObjectOutputStream out = new ObjectOutputStream(NullOutputStream.INSTANCE);
            out.writeObject(obj);
            out.close();
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    public static <T> T serializationRoundtrip(T obj) {
        try {
            StreamByteBuffer buffer = new StreamByteBuffer();
            ObjectOutputStream out = new ObjectOutputStream(buffer.getOutputStream());
            out.writeObject(obj);
            out.close();
            ClassLoaderObjectInputStream input = new ClassLoaderObjectInputStream(buffer.getInputStream(), obj.getClass().getClassLoader());
            return (T) input.readObject();
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }
}
