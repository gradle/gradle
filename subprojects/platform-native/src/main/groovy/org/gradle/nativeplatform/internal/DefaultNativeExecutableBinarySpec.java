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

package org.gradle.nativeplatform.internal;

import org.gradle.nativeplatform.NativeExecutableBinary;
import org.gradle.nativeplatform.NativeExecutableBinarySpec;
import org.gradle.nativeplatform.tasks.AbstractLinkTask;
import org.gradle.nativeplatform.tasks.InstallExecutable;

import java.io.File;

public class DefaultNativeExecutableBinarySpec extends AbstractNativeBinarySpec implements NativeExecutableBinary, NativeExecutableBinarySpecInternal {
    private final NativeExecutableBinarySpec.NativeBinaryTasks tasks = new DefaultNativeBinaryTasks(this);
    private File executableFile;

    public File getExecutableFile() {
        return executableFile;
    }

    public void setExecutableFile(File executableFile) {
        this.executableFile = executableFile;
    }

    public File getPrimaryOutput() {
        return getExecutableFile();
    }

    public NativeExecutableBinarySpec.NativeBinaryTasks getTasks() {
        return tasks;
    }

    public static class DefaultNativeBinaryTasks extends AbstractNativeBinarySpec.DefaultNativeBinaryTasks implements NativeExecutableBinarySpec.NativeBinaryTasks {
        public DefaultNativeBinaryTasks(NativeBinarySpecInternal binary) {
            super(binary);
        }

        public AbstractLinkTask getLink() {
            return findSingleTaskWithType(AbstractLinkTask.class);
        }

        public InstallExecutable getInstall() {
            return findSingleTaskWithType(InstallExecutable.class);
        }
    }
}
