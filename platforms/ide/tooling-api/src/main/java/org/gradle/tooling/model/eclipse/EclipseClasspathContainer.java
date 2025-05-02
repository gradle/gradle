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

package org.gradle.tooling.model.eclipse;

/**
 * Eclipse classpath entry used by third-party plugins to contribute to the project's classpath.
 *
 * @since 3.0
 */
public interface EclipseClasspathContainer extends EclipseClasspathEntry {

    /**
     * Returns the path of this container.
     *
     * @return The path of this container. Does not return null.
     */
    String getPath();

    /**
     * Marks this container as exported.
     *
     * @return whether this container needs to be exported.
     */
    boolean isExported();
}
