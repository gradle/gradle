/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.tasks.wrapper.internal;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;
import org.gradle.util.internal.GUtil;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.Properties;

/**
 * Lazily reads the existing Wrapper properties file.
 */
public abstract class ExistingWrapperProperties implements ValueSource<Properties, ExistingWrapperProperties.Parameters> {
    public interface Parameters extends ValueSourceParameters {
        RegularFileProperty getPropertiesFile();
    }

    @Override
    public @Nullable Properties obtain() {
        File propertiesFile = getParameters().getPropertiesFile().getAsFile().get();
        return propertiesFile.isFile() ? GUtil.loadProperties(propertiesFile) : null;
    }
}
