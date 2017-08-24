/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.language.nativeplatform.internal;

import org.apache.commons.lang.StringUtils;

public class Names {
    private final String taskName;
    private final String dirName;

    private Names(String name) {
        StringBuilder taskName = new StringBuilder();
        StringBuilder dirName = new StringBuilder();
        int startLast = 0;
        int i = 0;
        for (; i < name.length(); i++) {
            if (Character.isUpperCase(name.charAt(i))) {
                if (i > startLast) {
                    append(name, startLast, i, taskName, dirName);
                }
                startLast = i;
            }
        }
        if (i > startLast) {
            append(name, startLast, i, taskName, dirName);
        }
        this.taskName = taskName.toString();
        this.dirName = dirName.toString();
    }

    public static Names of(String name) {
        return new Names(name);
    }

    public String getTaskName(String action) {
        return action + taskName;
    }

    public String getCompileTaskName(String language) {
        return "compile" + taskName + StringUtils.capitalize(language);
    }

    // Includes trailing '/'
    public String getDirName() {
        return dirName;
    }

    private void append(String name, int start, int end, StringBuilder taskName, StringBuilder dirName) {
        dirName.append(Character.toLowerCase(name.charAt(start)));
        dirName.append(name.substring(start + 1, end));
        dirName.append('/');
        if (start != 0 || end != 4 || !name.startsWith("main")) {
            taskName.append(Character.toUpperCase(name.charAt(start)));
            taskName.append(name.substring(start + 1, end));
        }
    }
}
