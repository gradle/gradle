/*
 * Copyright 2026 the original author or authors.
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


package org.gradle.api.problems.internal.location;

import org.gradle.internal.code.Locality;

/**
 * The application of a script to a target with {@code apply from:}.
 * The script of a project, of the settings or of an init script is not an application: the project or build frame stands for it.
 */
public final class ScriptApplicationFrame extends Frame {

    private final String scriptName;

    public ScriptApplicationFrame(String scriptName, Locality locality) {
        super("script application " + scriptName, locality);
        this.scriptName = scriptName;
    }

    public String getScriptName() {
        return scriptName;
    }

    @Override
    public boolean isAuthored() {
        return false;
    }
}
