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

package org.gradle.normalization;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.internal.HasInternalProtocol;

/**
 * Configuration of runtime classpath normalization.
 *
 * @since 4.0
 */
@HasInternalProtocol
public interface RuntimeClasspathNormalization extends InputNormalization {
    /**
     * Ignore resources in classpath entries matching {@code pattern}.
     */
    void ignore(String pattern);

    /**
     * Configures the normalization strategy for the {@code META-INF} directory in archives.
     *
     * @since 6.6
     */
    @Incubating
    void metaInf(Action<? super MetaInfNormalization> configuration);
}
