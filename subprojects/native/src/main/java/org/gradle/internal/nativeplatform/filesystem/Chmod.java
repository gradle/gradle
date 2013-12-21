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

package org.gradle.internal.nativeplatform.filesystem;

import java.io.File;
import java.io.IOException;

public interface Chmod {
    /**
     * Changes the Unix permissions of a provided file. Implementations that don't
     * support Unix permissions may choose to ignore this request.
     *
     * @param file the file to change permissions on
     * @param mode the permissions, e.g. 0755
     * @throws java.io.FileNotFoundException if {@code file} doesn't exist
     * @throws IOException if the permissions can't be changed
     */
    public void chmod(File file, int mode) throws IOException;
}
