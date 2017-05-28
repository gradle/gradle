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
package org.gradle.internal.resource;

import org.gradle.api.Transformer;

import java.io.File;
import java.io.InputStream;

public abstract class AbstractExternalResource implements ExternalResource {
    @Override
    public String toString() {
        return getDisplayName();
    }

    public ExternalResourceReadResult<Void> writeTo(File destination) {
        ExternalResourceReadResult<Void> result = writeToIfPresent(destination);
        if (result == null) {
            throw ResourceExceptions.getMissing(getURI());
        }
        return result;
    }

    public <T> ExternalResourceReadResult<T> withContent(Transformer<? extends T, ? super InputStream> readAction) {
        ExternalResourceReadResult<T> result = withContentIfPresent(readAction);
        if (result == null) {
            throw ResourceExceptions.getMissing(getURI());
        }
        return result;
    }

    @Override
    public <T> ExternalResourceReadResult<T> withContent(ContentAction<? extends T> readAction) {
        ExternalResourceReadResult<T> result = withContentIfPresent(readAction);
        if (result == null) {
            throw ResourceExceptions.getMissing(getURI());
        }
        return result;
    }
}
