/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.tooling.model.idea;

import org.gradle.tooling.model.SourceDirectory;

/**
 * IDEA source directory.
 *
 * @since 1.0-milestone-5
 */
public interface IdeaSourceDirectory extends SourceDirectory {
    /**
     * Return {@code true} if this source directory is generated.
     *
     * @return true if source directory is generated
     * @since 2.2
     */
    boolean isGenerated();
}
