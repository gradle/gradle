/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.api.internal.project;

import com.google.common.primitives.Ints;
import org.gradle.api.Project;

public class ProjectOrderingUtil {
    public static int compare(Project left, Project right) {
        return compare(owner(left), owner(right));
    }

    public static int compare(ProjectState left, ProjectState right) {
        int depthCompare = depthCompare(left, right);
        if (depthCompare == 0) {
            return left.getProjectPath().compareTo(right.getProjectPath());
        } else {
            return depthCompare;
        }
    }

    public static int depthCompare(Project left, Project right) {
        return depthCompare(owner(left), owner(right));
    }

    public static int depthCompare(ProjectState left, ProjectState right) {
        return Ints.compare(left.getDepth(), right.getDepth());
    }

    private static ProjectState owner(Project project) {
        if (!(project instanceof ProjectInternal)) {
            throw new IllegalArgumentException("Unexpected Project implementation");
        }
        return ((ProjectInternal) project).getOwner();
    }
}
