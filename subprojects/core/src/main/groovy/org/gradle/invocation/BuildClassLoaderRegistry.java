/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.invocation;

public interface BuildClassLoaderRegistry {
    /**
     * Registers a {@code ClassLoader} to make visible to all scripts.
     */
    void addRootClassLoader(ClassLoader classLoader);

    /**
     * Returns the root {@code ClassLoader} to use for all scripts, including init and settings scripts. This {@code ClassLoader} exposes the Gradle API
     * plus any classes that are exposed using {@link #addRootClassLoader(ClassLoader)}.
     *
     * <p>This {@code ClassLoader} is also used to locate plugins by id.</p>
     */
    ClassLoader getScriptClassLoader();
}
