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

package org.gradle.internal.nativeintegration.filesystem.services;

import org.gradle.internal.nativeintegration.filesystem.FileModeAccessor;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

class FallbackStat implements FileModeAccessor {
    @Override
    public int getUnixMode(File f, boolean followLinks) throws IOException {
        if (f.isDirectory()) {
            return FileSystem.DEFAULT_DIR_MODE;
        } else if (f.exists()) {
            return FileSystem.DEFAULT_FILE_MODE;
        } else {
            throw new FileNotFoundException(String.format("File '%s' not found.", f));
        }
    }
}
