/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.api.internal

import org.gradle.api.Project

/**
 * @author Hans Dockter
 */
class PathOrder {
    static int compareTo(String path1, String path2) {
        int depth1 = path1.split(Project.PATH_SEPARATOR).size()
        int depth2 = path2.split(Project.PATH_SEPARATOR).size()
        if (depth1 == depth2) {
            return path1.compareTo(path2)
        } else {
            return depth1.compareTo(depth2)
        }
    }
}