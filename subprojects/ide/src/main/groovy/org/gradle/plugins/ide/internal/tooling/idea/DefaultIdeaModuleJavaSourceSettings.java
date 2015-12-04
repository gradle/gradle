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
import org.gradle.plugins.ide.internal.tooling.java.DefaultJavaSourceSettings;

public class DefaultIdeaModuleJavaSourceSettings extends DefaultJavaSourceSettings {

    private boolean sourceLanguageLevelInherited;

    public DefaultIdeaModuleJavaSourceSettings setSourceLanguageLevelInherited(boolean sourceLanguageLevelInherited) {
        this.sourceLanguageLevelInherited = sourceLanguageLevelInherited;
        return this;
    }

    public DefaultIdeaModuleJavaSourceSettings setSourceLanguageLevel(JavaVersion sourceLanguageLevel) {
        super.setSourceLanguageLevel(sourceLanguageLevel);
        return this;
    }

    public DefaultIdeaModuleJavaSourceSettings setTargetBytecodeLevel(JavaVersion targetBytecodeLevel) {
        super.setTargetBytecodeLevel(targetBytecodeLevel);
        return this;
    }

    public DefaultIdeaModuleJavaSourceSettings setJavaRuntime(DefaultJavaRuntime javaRuntime) {
        super.setJavaRuntime(javaRuntime);
        return this;
    }

    public boolean isSourceLanguageLevelInherited() {
        return sourceLanguageLevelInherited;
    }
}
