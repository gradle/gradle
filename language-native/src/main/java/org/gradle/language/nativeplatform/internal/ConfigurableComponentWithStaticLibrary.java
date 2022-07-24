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
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Property;
import org.gradle.language.ComponentWithOutputs;
import org.gradle.language.nativeplatform.ComponentWithObjectFiles;
import org.gradle.language.nativeplatform.ComponentWithStaticLibrary;
import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.nativeplatform.tasks.CreateStaticLibrary;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;

/**
 * A configurable view of a component that produces a static library. This should become public in some form.
 */
public interface ConfigurableComponentWithStaticLibrary extends ComponentWithStaticLibrary, ComponentWithObjectFiles, ComponentWithOutputs, ComponentWithNames {
    PlatformToolProvider getPlatformToolProvider();

    NativePlatform getNativePlatform();

    @Override
    Property<RegularFile> getLinkFile();

    @Override
    Property<Task> getLinkFileProducer();

    @Override
    Property<CreateStaticLibrary> getCreateTask();

    @Override
    ConfigurableFileCollection getOutputs();
}
