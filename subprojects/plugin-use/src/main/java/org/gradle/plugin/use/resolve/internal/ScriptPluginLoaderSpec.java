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
package org.gradle.plugin.use.resolve.internal;

public class ScriptPluginLoaderSpec {

    private final String displayName;
    private final String scriptContent;
    private final String scriptContentHash;

    private final String loaderClassSimpleName;
    private final String loaderClassBinaryName;
    private final String loaderClassFilePath;

    public ScriptPluginLoaderSpec(String displayName, String scriptContent, String scriptContentHash) {
        this.displayName = displayName;
        this.scriptContent = scriptContent;
        this.scriptContentHash = scriptContentHash;

        loaderClassSimpleName = ScriptPluginLoaderClassGenerator.SYNTHETIC_LOADER_CLASSNAME_PREFIX + scriptContentHash;
        loaderClassBinaryName = ScriptPluginLoaderClassGenerator.SYNTHETIC_LOADER_PACKAGE_NAME + "." + loaderClassSimpleName;
        loaderClassFilePath = ScriptPluginLoaderClassGenerator.SYNTHETIC_LOADER_PACKAGE_PATH + "/" + loaderClassSimpleName + ".class";
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getScriptContent() {
        return scriptContent;
    }

    public String getScriptContentHash() {
        return scriptContentHash;
    }

    public String getLoaderClassSimpleName() {
        return loaderClassSimpleName;
    }

    public String getLoaderClassBinaryName() {
        return loaderClassBinaryName;
    }

    public String getLoaderClassFilePath() {
        return loaderClassFilePath;
    }
}
