/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.plugins.ide.internal.tooling.eclipse;

import org.gradle.api.JavaVersion;
import org.gradle.plugins.ide.internal.tooling.java.DefaultInstalledJdk;

import java.io.Serializable;

public class DefaultEclipseJavaSourceSettings implements Serializable {
    private JavaVersion sourceLanguageLevel;
    private JavaVersion targetBytecodeVersion;
    private DefaultInstalledJdk jdk;

    public DefaultEclipseJavaSourceSettings setSourceLanguageLevel(JavaVersion sourceLanguageLevel) {
        this.sourceLanguageLevel = sourceLanguageLevel;
        return this;
    }

    public DefaultEclipseJavaSourceSettings setTargetBytecodeVersion(JavaVersion targetBytecodeLevel) {
        this.targetBytecodeVersion = targetBytecodeLevel;
        return this;
    }

    public DefaultEclipseJavaSourceSettings setJdk(DefaultInstalledJdk targetRuntime) {
        this.jdk = targetRuntime;
        return this;
    }

    public JavaVersion getSourceLanguageLevel() {
        return sourceLanguageLevel;
    }

    public JavaVersion getTargetBytecodeVersion() {
        return targetBytecodeVersion;
    }

    public DefaultInstalledJdk getJdk() {
        return jdk;
    }
}
