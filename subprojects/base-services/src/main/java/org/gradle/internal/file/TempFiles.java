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

package org.gradle.internal.file;

import com.google.common.base.Preconditions;

import javax.annotation.CheckReturnValue;
import java.io.File;
import java.io.IOException;

/**
 * Security safe API's for creating temporary files.
 */
public final class TempFiles {

    private TempFiles() {
        /* no-op */
    }

    /**
     * Improves the security guarantees of {@link File#createTempFile(String, String, File)}.
     *
     * @see File#createTempFile(String, String, File)
     */
    @CheckReturnValue
    public static File createTempFile(String prefix, String suffix, File directory) throws IOException {
        Preconditions.checkNotNull(directory, "The `directory` argument must not be null as this will default to the system temporary directory");
        return File.createTempFile(prefix, suffix, directory);
    }

}
