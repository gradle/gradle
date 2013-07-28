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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine;

import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.cache.BinaryStore;
import org.gradle.api.internal.file.TemporaryFileProvider;

import java.io.*;
import java.util.LinkedList;
import java.util.List;

import static org.gradle.internal.UncheckedException.throwAsUncheckedException;

//Draft, needs rework, along with BinaryStore interface, etc.
public class ResolutionResultsStoreFactory implements Closeable {
    private final TemporaryFileProvider temp;
    private final List<File> deleteMe = new LinkedList<File>();

    public ResolutionResultsStoreFactory(TemporaryFileProvider temp) {
        this.temp = temp;
    }

    public BinaryStore createStore(ConfigurationInternal configuration) {
        String id = configuration.getPath().replaceAll(":", "-");
        final File file = temp.createTemporaryFile("gradle-" + id + "-result", ".bin");
        file.deleteOnExit();
        deleteMe.add(file);
        return new SimpleBinaryStore(file);
    }

    public void close() throws IOException {
        for (File file : deleteMe) {
            file.delete();
        }
    }

    private static class SimpleBinaryStore implements BinaryStore {
        private File file;

        public SimpleBinaryStore(File file) {
            this.file = file;
        }

        public DataOutputStream getOutput() {
            try {
                return new DataOutputStream(new FileOutputStream(file));
            } catch (FileNotFoundException e) {
                throw throwAsUncheckedException(e);
            }
        }

        public DataInputStream getInput() {
            try {
                return new DataInputStream(new FileInputStream(file));
            } catch (FileNotFoundException e) {
                throw throwAsUncheckedException(e);
            }
        }
    }
}
