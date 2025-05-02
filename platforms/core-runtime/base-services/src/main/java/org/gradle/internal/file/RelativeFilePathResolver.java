/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.internal.file;

/**
 * Resolves a path object relative to some base directory.
 */
public interface RelativeFilePathResolver {
    /**
     * Converts the given path to a relative path.
     */
    String resolveAsRelativePath(Object path);

    /**
     * Converts the given path to a path that is useful to display to a human, for example in logging or error reports.
     * Generally attempts to use a relative path, but may switch to an absolute path for files outside and some distance from the base directory.
     */
    String resolveForDisplay(Object path);
}
