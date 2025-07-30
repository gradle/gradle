/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.initialization;

/**
 * File and directory names that are used for the discovery of the build logic.
 */
public interface BuildLogicFiles {

    String DEFAULT_SETTINGS_FILE = "settings.gradle";

    /**
     * Base name for discovery of settings scripts like <code>settings.gradle</code> or <code>settings.gradle.kts</code>.
     *
     * @see org.gradle.internal.scripts.ScriptingLanguages
     */
    String SETTINGS_FILE_BASENAME = "settings";

    /**
     * Base name for discovery of build scripts like <code>build.gradle</code> or <code>build.gradle.kts</code>.
     *
     * @see org.gradle.internal.scripts.ScriptingLanguages
     */
    String BUILD_FILE_BASENAME = "build";

    /**
     * Reserved directory name for automatically discovered included-like build with build logic.
     */
    String BUILD_SOURCE_DIRECTORY = "buildSrc";

}
