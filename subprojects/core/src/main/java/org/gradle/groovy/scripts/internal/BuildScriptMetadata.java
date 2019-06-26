/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.groovy.scripts.internal;

/**
 * Data extracted from an initial pass of a build script at compile time.
 */
public class BuildScriptMetadata {
    private final int pluginsBlockLineNumber;

    public BuildScriptMetadata(int pluginsBlockLineNumber) {
        this.pluginsBlockLineNumber = pluginsBlockLineNumber;
    }

    /**
     * @return The line number of the plugins block, or 0 if no plugins block was detected.
     */
    public int getPluginsBlockLineNumber() {
        return pluginsBlockLineNumber;
    }
}
