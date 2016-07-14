/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.configuration;

import org.gradle.api.Incubating;
import org.gradle.api.Nullable;

/**
 * A {@link ScriptPluginFactory} SPI suitable for use with Java's {@code ServiceLoader}.
 *
 * The SPI implementation can get access to Gradle services via {@link javax.inject.Inject}
 * style dependency injection.
 *
 * @see ScriptPluginFactorySelector
 * @since 2.14
 */
@Incubating
public interface ScriptPluginFactoryProvider {

    /**
     * Returns a {@link ScriptPluginFactory} suitable for creating a {@link ScriptPlugin}
     * instances for files with the given name, otherwise {@code null}.
     */
    @Nullable
    ScriptPluginFactory getFor(String fileName);
}
