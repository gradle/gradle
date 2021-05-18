/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.process;

import org.gradle.internal.HasInternalProtocol;

import java.util.Map;

/**
 * <p>Specifies the options to use to fork a Java process.</p>
 */
@HasInternalProtocol
public interface JavaForkOptions extends ProviderAwareJvmForkOptions, ProcessForkOptions {
    /**
     * Copies these options to the given options.
     *
     * @param options The target options.
     * @return this
     */
    JavaForkOptions copyTo(JavaForkOptions options);

    /**
     * {@inheritDoc}
     */
    @Override
    JavaForkOptions systemProperties(Map<String, ?> properties);

    /**
     * {@inheritDoc}
     */
    @Override
    JavaForkOptions systemProperty(String name, Object value);

    /**
     * {@inheritDoc}
     */
    @Override
    JavaForkOptions jvmArgs(Iterable<?> arguments);

    /**
     * {@inheritDoc}
     */
    @Override
    JavaForkOptions jvmArgs(Object... arguments);

    /**
     * {@inheritDoc}
     */
    @Override
    JavaForkOptions bootstrapClasspath(Object... classpath);
}
