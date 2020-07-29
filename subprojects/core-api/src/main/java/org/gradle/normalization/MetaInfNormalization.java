/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.normalization;

import org.gradle.api.Incubating;

/**
 * Configuration of manifest normalization.
 *
 * @since 6.6
 */
@Incubating
public interface MetaInfNormalization {
    /**
     * Ignore all files and subdirectories in the {@code META-INF} directory within archives.
     *
     * @since 6.6
     */
    @Incubating
    void ignoreCompletely();

    /**
     * Ignore the {@code META-INF/MANIFEST.MF} file within archives.
     *
     * @since 6.6
     */
    @Incubating
    void ignoreManifest();

    /**
     * Ignore attributes in {@code META-INF/MANIFEST.MF} within archives matching {@code name}. {@code name} is matched case-insensitively with the manifest attribute name.
     *
     * @since 6.6
     */
    @Incubating
    void ignoreAttribute(String name);

    /**
     * Ignore keys in properties files stored in {@code META-INF} within archives matching {@code name}. {@code name} is matched case-sensitively with the property key.
     *
     * @since 6.6
     */
    @Incubating
    void ignoreProperty(String name);
}
