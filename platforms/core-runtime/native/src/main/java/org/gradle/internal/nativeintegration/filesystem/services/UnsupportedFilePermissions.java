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

import org.gradle.internal.nativeintegration.filesystem.FileModeAccessor;
import org.gradle.internal.nativeintegration.filesystem.FileModeMutator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

public class UnsupportedFilePermissions implements FileModeAccessor, FileModeMutator {
    private static final Logger LOGGER = LoggerFactory.getLogger(UnsupportedFilePermissions.class);
    private final AtomicBoolean warned = new AtomicBoolean();
    private final FallbackStat stat = new FallbackStat();
    private final EmptyChmod chmod = new EmptyChmod();

    @Override
    public int getUnixMode(File f, boolean followLinks) throws Exception {
        maybeWarn();
        return stat.getUnixMode(f, followLinks);
    }

    @Override
    public void chmod(File file, int mode) throws Exception {
        maybeWarn();
        chmod.chmod(file, mode);
    }

    private void maybeWarn() {
        if (warned.compareAndSet(false, true)) {
            LOGGER.warn("Support for reading or changing file permissions is only available on this platform using Java 7 or later.");
        }
    }
}
