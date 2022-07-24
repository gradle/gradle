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

package org.gradle.api.file;

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;

import javax.annotation.Nullable;
import java.io.File;

/**
 * Represents some configurable regular file location, whose value is mutable.
 *
 * <p>
 * You can create a {@link RegularFileProperty} using {@link ObjectFactory#fileProperty()}.
 * </p>
 *
 * <p><b>Note:</b> This interface is not intended for implementation by build script or plugin authors.</p>
 *
 * @since 4.3
 */
public interface RegularFileProperty extends FileSystemLocationProperty<RegularFile> {
    /**
     * {@inheritDoc}
     */
    @Override
    RegularFileProperty value(@Nullable RegularFile value);

    /**
     * {@inheritDoc}
     */
    @Override
    RegularFileProperty value(Provider<? extends RegularFile> provider);

    /**
     * {@inheritDoc}
     */
    @Override
    RegularFileProperty fileValue(@Nullable File file);

    /**
     * {@inheritDoc}
     */
    @Override
    RegularFileProperty fileProvider(Provider<File> provider);

    /**
     * {@inheritDoc}
     */
    @Override
    RegularFileProperty convention(RegularFile value);

    /**
     * {@inheritDoc}
     */
    @Override
    RegularFileProperty convention(Provider<? extends RegularFile> provider);
}
