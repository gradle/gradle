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

package org.gradle.api.io;

import java.io.*;

/**
 * @author Tom Eyckmans
 */
public class DefaultIoFactory implements IoFactory {
    public BufferedReader createBufferedReader(File file) throws IOException {
        return new BufferedReader(new FileReader(file));
    }

    public BufferedWriter createBufferedWriter(File file) throws IOException {
        return new BufferedWriter(new FileWriter(file));
    }

    public BufferedInputStream createBufferedInputStream(File file) throws IOException {
        return new BufferedInputStream(new FileInputStream(file));
    }

    public BufferedOutputStream createBufferedOutputStream(File file) throws IOException {
        return new BufferedOutputStream(new FileOutputStream(file));
    }
}
