/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.api.Incubating;

/**
 * Information about a symbolic link in a file tree.
 *
 * @since 8.6
 */
@Incubating
public interface SymbolicLinkDetails {
    /**
     * The target of the symbolic link
     *
     * @return the target of the symbolic link.
     * @since 8.6
     */
    String getTarget();

    /**
     * Check if target exists
     *
     * @return true if target exists
     * @since 8.6
     */
    boolean targetExists();

    /**
     * Check if the link is relative
     *
     * @return true if the link is relative.
     * @since 8.6
     */
    boolean isRelative();
}
