/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.language.cpp.plugins

import org.gradle.api.Plugin
import org.gradle.api.Task
import org.gradle.language.AbstractNativeComponentPluginTest
import org.gradle.language.base.LanguageSourceSet
import org.gradle.language.cpp.CppSourceSet
import org.gradle.language.cpp.tasks.CppCompile

class CppPluginTest extends AbstractNativeComponentPluginTest {
    @Override
    Class<? extends Plugin> getPluginClass() {
        return CppPlugin
    }

    @Override
    Class<? extends Task> getCompileTaskClass() {
        return CppCompile
    }

    @Override
    Class<? extends LanguageSourceSet> getSourceSetClass() {
        return CppSourceSet
    }

    @Override
    String getPluginName() {
        return "cpp"
    }
}
