/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.incremental;

import java.io.*;

import static org.apache.commons.io.IOUtils.closeQuietly;

public class DummySerializer {
    public static void writeTargetTo(File outputFile, Object target) {
        try {
            FileOutputStream out = new FileOutputStream(outputFile);
            ObjectOutputStream objectStr = new ObjectOutputStream(out);
            objectStr.writeObject(target);
            objectStr.flush();
            objectStr.close();
            out.close();
        } catch (IOException e) {
            throw new RuntimeException("Problems writing to the output file " + outputFile, e);
        }
    }

    public static Object readFrom(File inputFile) {
        FileInputStream in = null;
        ObjectInputStream objectStr = null;
        try {
            in = new FileInputStream(inputFile);
            objectStr = new ObjectInputStream(in);
            return objectStr.readObject();
        } catch (Exception e) {
            throw new RuntimeException("Problems reading the class tree to the output file " + inputFile, e);
        } finally {
            closeQuietly(in);
            closeQuietly(objectStr);
        }
    }
}
