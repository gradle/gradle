/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.initialization;

import org.gradle.internal.scripts.DefaultScriptFileResolver;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public abstract class DirectoryInitScriptFinder implements InitScriptFinder {

    protected void findScriptsInDir(File initScriptsDir, Collection<File> scripts) {
        if (!initScriptsDir.isDirectory()) {
            return;
        }
        List<File> found = initScriptsIn(initScriptsDir);
        Collections.sort(found);
        scripts.addAll(found);
    }

    @Nullable
    protected File resolveScriptFile(File dir, String basename) {
        return resolver().resolveScriptFile(dir, basename);
    }

    private List<File> initScriptsIn(File initScriptsDir) {
        return resolver().findScriptsIn(initScriptsDir);
    }

    private DefaultScriptFileResolver resolver() {
        return new DefaultScriptFileResolver();
    }
}
