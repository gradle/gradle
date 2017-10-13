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
package org.gradle.internal.scripts;

import java.io.File;
import javax.annotation.Nullable;

/**
 * Resolves script files according to available {@link org.gradle.scripts.ScriptingLanguage} providers.
 *
 * @since 4.0
 */
public interface ScriptFileResolver {

    /**
     * Resolves a script file.
     *
     * @param dir the directory in which to search
     * @param basename the base name of the script file, i.e. its file name excluding the extension
     * @return the resolved script file present on disk, or {@literal null} if none were found
     */
    @Nullable
    File resolveScriptFile(File dir, String basename);
}
