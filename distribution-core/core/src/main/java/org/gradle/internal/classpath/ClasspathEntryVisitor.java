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

package org.gradle.internal.classpath;

import org.gradle.api.file.RelativePath;

import java.io.IOException;

public interface ClasspathEntryVisitor {
    /**
     * Visits the contents of a classpath element.
     */
    void visit(Entry entry) throws IOException;

    interface Entry {
        String getName();

        RelativePath getPath();

        /**
         * Can be called at most once for a given entry. If not called, content is skipped.
         */
        byte[] getContent() throws IOException;
    }
}
