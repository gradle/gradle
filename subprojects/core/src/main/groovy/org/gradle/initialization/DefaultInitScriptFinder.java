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
import java.util.ArrayList;

/**
 * Simple finder that "finds" all the init scripts that were explicitly added to the start parameters.
 */
public class DefaultInitScriptFinder implements InitScriptFinder {
    public List<ScriptSource> findScripts(GradleInternal gradle) {
        List<File> scriptFiles = gradle.getStartParameter().getInitScripts();
        List<ScriptSource> scripts = new ArrayList<ScriptSource>(scriptFiles.size());
        for (File file : scriptFiles) {
            scripts.add(new UriScriptSource("initialization script", file));
        }

        return scripts;
    }
}