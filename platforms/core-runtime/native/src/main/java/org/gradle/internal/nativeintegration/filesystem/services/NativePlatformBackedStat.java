/*
 * Copyright 2014 the original author or authors.
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

import net.rubygrapefruit.platform.file.PosixFiles;
import org.gradle.internal.file.FileModeAccessor;

import java.io.File;

class NativePlatformBackedStat implements FileModeAccessor {
    private final PosixFiles posixFiles;

    public NativePlatformBackedStat(PosixFiles posixFiles) {
        this.posixFiles = posixFiles;
    }

    @Override
    public int getUnixMode(File f) {
        return posixFiles.getMode(f, true);
    }
}
