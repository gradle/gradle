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
package org.gradle.api.testing.fabric;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * @author Tom Eyckmans
 */
public class TestMethodProcessResult implements Serializable {

    private String methodName;
    private Throwable thrownException;

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.writeUTF(methodName);
        out.writeObject(thrownException);
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        methodName = in.readUTF();
        thrownException = (Throwable) in.readObject();
    }

}
