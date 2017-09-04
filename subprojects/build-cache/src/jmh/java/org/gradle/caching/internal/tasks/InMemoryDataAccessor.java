/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.caching.internal.tasks;

import org.openjdk.jmh.annotations.Level;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class InMemoryDataAccessor implements DataAccessor {
    @Override
    public DataSource createSource(String name, byte[] bytes, Level level) {
        return new Source(name, bytes);
    }

    @Override
    public DataTarget createTarget(String name, Level level) {
        return new Target(name);
    }

    @Override
    public DataTargetFactory createTargetFactory(final String root, Level level) {
        return new DataTargetFactory() {
            @Override
            public DataTarget createDataTarget(String name) {
                return new Target(root + "/" + name);
            }
        };
    }

    private static class Source implements DataSource {
        private final String name;
        private final byte[] data;

        public Source(String name, byte[] data) {
            this.name = name;
            this.data = data;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public InputStream openInput() throws IOException {
            return new ByteArrayInputStream(data);
        }

        @Override
        public long getLength() throws IOException {
            return data.length;
        }
    }

    private static class Target implements DataTarget {
        private final String name;
        private final ByteArrayOutputStream data = new ByteArrayOutputStream();

        public Target(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public OutputStream openOutput() throws IOException {
            return data;
        }

        @Override
        public DataSource toSource() throws IOException {
            return new Source(name, data.toByteArray());
        }
    }
}
