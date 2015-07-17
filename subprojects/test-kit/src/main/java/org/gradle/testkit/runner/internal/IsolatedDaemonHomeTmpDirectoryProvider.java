/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.testkit.runner.internal;

import org.gradle.api.UncheckedIOException;
import org.gradle.internal.SystemProperties;

import java.io.File;

public class IsolatedDaemonHomeTmpDirectoryProvider implements TmpDirectoryProvider {
    public final static String DIR_NAME = ".gradle-test-kit";
    private final File parentDir;

    public IsolatedDaemonHomeTmpDirectoryProvider() {
        this(new File(SystemProperties.getInstance().getUserHome()));
    }

    IsolatedDaemonHomeTmpDirectoryProvider(File parentDir) {
        this.parentDir = parentDir;
    }

    public File createDir() {
        File tmpDir = new File(parentDir, DIR_NAME);
        if (!tmpDir.mkdirs() && !tmpDir.isDirectory()) {
            throw new UncheckedIOException(String.format("Unable to create temporary directory %s", tmpDir.getAbsolutePath()));
        }

        return tmpDir;
    }
}
