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

import org.gradle.internal.nativeplatform.jna.LibC;
import org.gradle.internal.os.OperatingSystem;
import org.jruby.ext.posix.BaseNativePOSIX;
import org.jruby.ext.posix.FileStat;
import org.jruby.ext.posix.Linux64FileStat;

import java.io.File;
import java.io.IOException;

class LibCStat implements Stat {
    private final LibC libc;
    private final FilePathEncoder encoder;
    private final OperatingSystem operatingSystem;
    private final BaseNativePOSIX nativePOSIX;

    public LibCStat(LibC libc, OperatingSystem operatingSystem, BaseNativePOSIX nativePOSIX, FilePathEncoder encoder) {
        this.libc = libc;
        this.operatingSystem = operatingSystem;
        this.nativePOSIX = nativePOSIX;
        this.encoder = encoder;
    }

    public int getUnixMode(File f) throws IOException {
        FileStat stat = nativePOSIX.allocateStat();
        initPlatformSpecificStat(stat, encoder.encode(f));
        return stat.mode() & 0777;
    }

    private void initPlatformSpecificStat(FileStat stat, byte[] encodedFilePath) {
        if (operatingSystem.isMacOsX()) {
            libc.stat(encodedFilePath, stat);
        } else {
            final int statVersion = stat instanceof Linux64FileStat ? 3 : 0;
            libc.__xstat64(statVersion, encodedFilePath, stat);
        }
    }
}
