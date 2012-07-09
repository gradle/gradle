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

package org.gradle.process.internal.streams;

import org.gradle.internal.CompositeStoppable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * By Szczepan Faber on 7/9/12
 */
public class DefaultProcessStreamHandler implements ProcessStreamHandler {

    public void handleStream(InputStream source, OutputStream destination) {
        byte[] buffer = new byte[2048];
        try {
            while (true) {
                int nread = source.read(buffer);
                if (nread < 0) {
                    break;
                }
                destination.write(buffer, 0, nread);
                destination.flush();
            }
            new CompositeStoppable(source, destination).stop();
        } catch (IOException e) {
            throw new RuntimeException("Problems handling process' streams.", e);
        }
    }
}
