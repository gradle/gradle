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

import org.gradle.nativeplatform.*;
import org.gradle.nativeplatform.tasks.InstallExecutable;
import org.gradle.nativeplatform.tasks.LinkExecutable;
import org.gradle.nativeplatform.tasks.ObjectFilesToBinary;
import org.gradle.platform.base.BinaryTasksCollection;
import org.gradle.platform.base.internal.BinaryTasksCollectionWrapper;

import java.io.File;

public class DefaultNativeExecutableBinarySpec extends AbstractNativeBinarySpec implements NativeExecutableBinary, NativeExecutableBinarySpecInternal {
    private final DefaultTasksCollection tasks = new DefaultTasksCollection(super.getTasks());
    private final NativeInstallationSpec installation = new NativeInstallationSpec();
    private final NativeExecutableFileSpec executable = new NativeExecutableFileSpec();

    @Override
    public NativeExecutableSpec getComponent() {
        return getComponentAs(NativeExecutableSpec.class);
    }

    @Override
    public NativeExecutableSpec getApplication() {
        return getComponentAs(NativeExecutableSpec.class);
    }

    @Override
    public NativeExecutableFileSpec getExecutable() {
        return executable;
    }

    @Override
    public File getExecutableFile() {
        return getExecutable().getFile();
    }

    @Override
    public NativeInstallationSpec getInstallation() {
        return installation;
    }

    @Override
    public File getPrimaryOutput() {
        return getExecutableFile();
    }

    @Override
    protected ObjectFilesToBinary getCreateOrLink() {
        return tasks.getLink();
    }

    @Override
    public NativeExecutableBinarySpec.TasksCollection getTasks() {
        return tasks;
    }

    private static class DefaultTasksCollection extends BinaryTasksCollectionWrapper implements NativeExecutableBinarySpec.TasksCollection {

        public DefaultTasksCollection(BinaryTasksCollection delegate) {
            super(delegate);
        }

        @Override
        public LinkExecutable getLink() {
            return findSingleTaskWithType(LinkExecutable.class);
        }

        @Override
        public InstallExecutable getInstall() {
            return findSingleTaskWithType(InstallExecutable.class);
        }
    }
}
