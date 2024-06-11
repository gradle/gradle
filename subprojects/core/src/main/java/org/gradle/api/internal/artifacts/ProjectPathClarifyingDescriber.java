/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.internal.artifacts;

import org.gradle.api.Describable;
import org.gradle.util.Path;

/**
 * Static utility class containing methods for describing a project path with clarifications
 * that may make it clearer to the reader in error messages.
 */
public final class ProjectPathClarifyingDescriber {
    private static final String PROJECT_PREFIX = "project ";

    private ProjectPathClarifyingDescriber() { /* not instantiable */}

    /**
     * Describes the given {@link Describable} object, clarifying when it references the root project.
     *
     * @param describable the object to describe
     * @return clarified description of the object
     */
    public static String describe(Describable describable) {
        return describe(describable.getDisplayName());
    }

    /**
     * Describes the given display path, clarifying when that path references the root project.
     *
     * @param displayPath the path to describe
     * @return clarified description of the path
     */
    public static String describe(String displayPath) {
        String pathAlone;
        if (displayPath.startsWith(PROJECT_PREFIX)) {
            pathAlone = displayPath.substring(PROJECT_PREFIX.length());
        } else {
            pathAlone = displayPath;
        }
        if (Path.path(pathAlone) == Path.ROOT) {
            return displayPath + " (the root project)";
        } else {
            return displayPath;
        }
    }
}
