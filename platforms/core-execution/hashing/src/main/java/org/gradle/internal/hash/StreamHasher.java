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
package org.gradle.internal.hash;

import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

@ServiceScope(Scope.Global.class)
public interface StreamHasher {
    /**
     * Returns the hash of the given input stream. The stream will not be closed by the method.
     */
    HashCode hash(InputStream inputStream) throws IOException;

    /**
     * Returns the hash of the given input stream while copying the data to the output stream.
     * The method will not close either stream.
     */
    HashCode hashCopy(InputStream inputStream, OutputStream outputStream) throws IOException;
}
