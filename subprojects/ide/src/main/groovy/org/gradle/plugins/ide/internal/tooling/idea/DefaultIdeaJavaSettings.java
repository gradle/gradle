/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.plugins.ide.internal.tooling.idea;

import org.gradle.api.JavaVersion;
import org.gradle.plugins.ide.internal.tooling.java.DefaultJavaRuntime;

import java.io.Serializable;

public class DefaultIdeaJavaSettings implements Serializable {
    private JavaVersion sourceLanguageLevel;
    private JavaVersion targetBytecodeLevel;
    private DefaultJavaRuntime targetRuntime;

    private boolean targetBytecodeLevelInherited;
    private boolean targetRuntimeInherited;


    public DefaultIdeaJavaSettings setSourceLanguageLevel(JavaVersion sourceLanguageLevel) {
        this.sourceLanguageLevel = sourceLanguageLevel;
        return this;
    }

    public DefaultIdeaJavaSettings setTargetBytecodeLevel(JavaVersion targetBytecodeLevel) {
        this.targetBytecodeLevel = targetBytecodeLevel;
        return this;
    }

    public DefaultIdeaJavaSettings setTargetRuntime(DefaultJavaRuntime targetRuntime) {
        this.targetRuntime = targetRuntime;
        return this;
    }

    public JavaVersion getSourceLanguageLevel() {
        return sourceLanguageLevel;
    }

    public JavaVersion getTargetBytecodeLevel() {
        return targetBytecodeLevel;
    }

    public DefaultJavaRuntime getTargetRuntime() {
        return targetRuntime;
    }

    public DefaultIdeaJavaSettings setTargetBytecodeLevelInherited(boolean targetBytecodeLevelInherited) {
        this.targetBytecodeLevelInherited = targetBytecodeLevelInherited;
        return this;
    }

    public DefaultIdeaJavaSettings setTargetRuntimeInherited(boolean targetRuntimeInherited) {
        this.targetRuntimeInherited = targetRuntimeInherited;
        return this;
    }

    public boolean isTargetBytecodeLevelInherited() {
        return targetBytecodeLevelInherited;
    }

    public boolean isTargetRuntimeInherited() {
        return targetRuntimeInherited;
    }
}
