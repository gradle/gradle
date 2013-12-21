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
package org.gradle.initialization;

public interface ClassLoaderRegistry {
    /**
     * Returns the root class loader shared by all builds. This class loader exposes the Gradle API and built-in plugins.
     */
    ClassLoader getGradleApiClassLoader();

    /**
     * Returns the class loader for the Gradle runtime.
     */
    ClassLoader getRuntimeClassLoader();

    /**
     * Returns the class loader for the coreImpl project.
     */
    ClassLoader getCoreImplClassLoader();

    /**
     * Returns the class loader for the plugins.
     */
    ClassLoader getPluginsClassLoader();
}
