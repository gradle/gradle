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

package org.gradle.plugins.ide.internal.tooling.model;

import java.io.Serializable;
import java.util.Comparator;

/**
 * Compares task names to create ordering for selector launching.
 */
public class TaskNameComparator implements Comparator<String>, Serializable {
    @Override
    public int compare(String taskName1, String taskName2) {
        int depthDiff = getDepth(taskName1) - getDepth(taskName2);
        if (depthDiff != 0) {
            return depthDiff;
        }
        return compareSegments(taskName1, taskName2);
    }

    private int compareSegments(String taskName1, String taskName2) {
        int colon1 = taskName1.indexOf(':');
        int colon2 = taskName2.indexOf(':');
        if (colon1 > 0 && colon2 > 0) {
            int diff = taskName1.substring(0, colon1).compareTo(taskName2.substring(0, colon2));
            if (diff != 0) {
                return diff;
            }
        }
        return colon1 == -1 ? taskName1.compareTo(taskName2) : compareSegments(taskName1.substring(colon1 + 1), taskName2.substring(colon2 + 1));
    }

    private int getDepth(String taskName) {
        int counter = 0;
        for (char c : taskName.toCharArray()) {
            if (c == ':') {
                counter++;
            }
        }
        return counter;
    }
}
