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
    private final File tmpDir;

    public IsolatedDaemonHomeTmpDirectoryProvider() {
        this(new File(SystemProperties.getInstance().getJavaIoTmpDir()));
    }

    IsolatedDaemonHomeTmpDirectoryProvider(File tmpDir) {
        this.tmpDir = new File(tmpDir, String.format(".gradle-test-kit-%s", SystemProperties.getInstance().getUserName()));
    }

    public File createDir() {
        if (!tmpDir.mkdirs() && !tmpDir.isDirectory()) {
            throw new UncheckedIOException(String.format("Unable to create temporary directory %s", tmpDir.getAbsolutePath()));
        }

        return tmpDir;
    }
}
