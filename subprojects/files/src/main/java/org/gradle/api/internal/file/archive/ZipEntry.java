/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.api.internal.file.archive;

import java.io.IOException;
import java.io.InputStream;

public interface ZipEntry {

    boolean isDirectory();

    String getName();

    /**
     * This method or {@link #withInputStream(InputStreamAction)} ()} can be called at most once per entry.
     */
    byte[] getContent() throws IOException;

    /**
     * Functional interface to run an action against a {@link InputStream}
     *
     * @param <T> the action's result.
     */
    interface InputStreamAction<T> {
        /**
         * action to run against the passed {@link InputStream}.
         */
        T run(InputStream inputStream) throws IOException;
    }

    /**
     * Declare an action to be run against this ZipEntry's content as a {@link InputStream}.
     * The {@link InputStream} passed to the {@link InputStreamAction#run(InputStream)} will
     * be closed right after the action's return.
     *
     * This method or {@link #getContent()} can be called at most once per entry.
     */
    <T> T withInputStream(InputStreamAction<T> action) throws IOException;

    /**
     * The size of the content in bytes, or -1 if not known.
     */
    int size();
}
