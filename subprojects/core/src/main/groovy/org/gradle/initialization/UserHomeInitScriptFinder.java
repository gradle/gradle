/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.api.internal.GradleInternal;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.groovy.scripts.UriScriptSource;

import java.io.File;
import java.util.List;

public class UserHomeInitScriptFinder implements InitScriptFinder {
    public static final String DEFAULT_INIT_SCRIPT_NAME = "init.gradle";

    private final InitScriptFinder finder;

    public UserHomeInitScriptFinder(InitScriptFinder finder) {
        this.finder = finder;
    }

    public List<ScriptSource> findScripts(GradleInternal gradle) {
        List<ScriptSource> scripts = finder.findScripts(gradle);

        File userHomeDir = gradle.getStartParameter().getGradleUserHomeDir();
        File userInitScript = new File(userHomeDir, DEFAULT_INIT_SCRIPT_NAME);
        if (userInitScript.isFile()) {
            scripts.add(new UriScriptSource("initialization script", userInitScript));
        }

        return scripts;
    }
}

