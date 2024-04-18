/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.performance.results;

/**
 * Moved here from OutputDirSelector to work around
 * <a href="https://issues.apache.org/jira/browse/GROOVY-10591">GROOVY-10591</a>
 */
public class OutputDirSelectorUtil {

    public static String fileSafeNameFor(String name) {
        String fileSafeName = name.trim().replaceAll("[^a-zA-Z0-9.-]", "-").replaceAll("-+", "-");
        return (fileSafeName.endsWith("-"))
            ? fileSafeName.substring(0, fileSafeName.length() - 1)
            : fileSafeName;
    }
}
