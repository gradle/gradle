/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.language.nativeplatform.internal;

import org.gradle.api.Task;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Property;
import org.gradle.language.ComponentWithOutputs;
import org.gradle.language.nativeplatform.ComponentWithExecutable;
import org.gradle.language.nativeplatform.ComponentWithInstallation;
import org.gradle.language.nativeplatform.ComponentWithObjectFiles;
import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.nativeplatform.tasks.InstallExecutable;
import org.gradle.nativeplatform.tasks.LinkExecutable;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;

/**
 * A configurable view of a component that produces an executable and installation. This should become public in some form.
 */
public interface ConfigurableComponentWithExecutable extends ComponentWithExecutable, ComponentWithInstallation, ComponentWithObjectFiles, ComponentWithOutputs, ComponentWithNames {
    PlatformToolProvider getPlatformToolProvider();

    NativePlatform getNativePlatform();

    @Override
    Property<LinkExecutable> getLinkTask();

    @Override
    Property<InstallExecutable> getInstallTask();

    @Override
    Property<RegularFile> getExecutableFile();

    @Override
    Property<Task> getExecutableFileProducer();

    Property<RegularFile> getDebuggerExecutableFile();

    @Override
    Property<Directory> getInstallDirectory();

    @Override
    ConfigurableFileCollection getOutputs();
}
