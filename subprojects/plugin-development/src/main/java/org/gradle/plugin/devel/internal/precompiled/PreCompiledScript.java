/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.plugin.devel.internal.precompiled;

import org.gradle.groovy.scripts.ScriptSource;

import java.io.File;

class PreCompiledScript {
    public static final String SCRIPT_PLUGIN_EXTENSION = ".gradle";
    private static final String PLUGIN_PREFIX = "plugins";
    private static final String BUILDSCRIPT_PREFIX = "script";

    private final ScriptSource scriptSource;

    PreCompiledScript(ScriptSource scriptSource) {
        this.scriptSource = scriptSource;
    }

    String getId() {
        // TODO should also detect package name and add it to the id
        return getFileNameWithoutExtension();
    }

    String getGeneratedPluginClassName() {
        return getFileNameWithoutExtension() + "Plugin";
    }

    String getPluginMetadataDirPath() {
        return getClassName() + "/" + PLUGIN_PREFIX + "-metadata";
    }

    String getBuildScriptMetadataDirPath() {
        return getClassName() + "/" + BUILDSCRIPT_PREFIX + "-metadata";
    }

    String getPluginClassesDirPath() {
        return getClassName() + "/" + PLUGIN_PREFIX + "-classes";
    }

    String getBuildScriptClassesDirPath() {
        return getClassName() + "/" + BUILDSCRIPT_PREFIX + "-classes";
    }

    File getScriptFile() {
        return new File(scriptSource.getFileName());
    }

    String getClassName() {
        // TODO should also include the package name
        return scriptSource.getClassName();
    }

    ScriptSource getSource() {
        return scriptSource;
    }

    private String getFileNameWithoutExtension() {
        String fileName = new File(scriptSource.getFileName()).getName();
        return fileName.substring(0, fileName.indexOf(SCRIPT_PLUGIN_EXTENSION));
    }
}
