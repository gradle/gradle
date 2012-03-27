/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.externalresource;

import java.io.IOException;
import java.io.InputStream;

public class MissingExternalResource extends AbstractExternalResource {
    private final String source;

    public MissingExternalResource(String source) {
        this.source = source;
    }

    @Override
    public String toString() {
        return "MissingResource: " + getName();
    }

    public String getName() {
        return source;
    }

    public boolean exists() {
        return false;
    }

    public long getLastModified() {
        return -1;
    }

    public long getContentLength() {
        return -1;
    }

    public boolean isLocal() {
        throw new UnsupportedOperationException();
    }

    public InputStream openStream() throws IOException {
        throw new UnsupportedOperationException();
    }
}
