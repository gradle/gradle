/*
 * Copyright 2021 the original author or authors.
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

import org.gradle.api.tasks.Nested;

import java.util.List;

/**
 * <p>Specifies the options to use to fork a JVM process that can accept {@link CommandLineArgumentProvider} objects.</p>
 *
 * @since 7.1
 */
public interface ProviderAwareJvmForkOptions extends JvmForkOptions {
    /**
     * Command line argument providers for the java process to fork.
     *
     * @since 4.6
     */
    @Nested
    List<CommandLineArgumentProvider> getJvmArgumentProviders();
}
