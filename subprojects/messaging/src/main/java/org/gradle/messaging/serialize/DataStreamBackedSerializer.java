/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.messaging.serialize;

import java.io.*;

public abstract class DataStreamBackedSerializer<T> implements Serializer<T> {
    public T read(InputStream instr) throws Exception {
        DataInputStream dataInputStream = new DataInputStream(instr);
        return read((DataInput) dataInputStream);
    }

    public void write(OutputStream outstr, T value) throws Exception {
        DataOutputStream output = new DataOutputStream(outstr);
        write((DataOutput) output, value);
        output.flush();
    }

    public abstract T read(DataInput dataInput) throws Exception;

    public abstract void write(DataOutput dataOutput, T value) throws IOException;
}
