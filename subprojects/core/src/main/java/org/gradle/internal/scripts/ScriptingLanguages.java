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
package org.gradle.internal.scripts;

import com.google.common.collect.ImmutableList;

import org.gradle.scripts.ScriptingLanguage;

import java.util.List;

/**
 * Registry of scripting languages.
 */
public final class ScriptingLanguages {

    private static final ImmutableList<ScriptingLanguage> ALL =
        ImmutableList.of(
            scriptingLanguage(".gradle", null),
            scriptingLanguage(".gradle.kts", "org.gradle.kotlin.dsl.provider.KotlinScriptPluginFactory"));

    static final ImmutableList<String> EXTENSIONS = extensionsOf(ALL);

    public static List<ScriptingLanguage> all() {
        return ALL;
    }

    private static ScriptingLanguage scriptingLanguage(final String extension, final String scriptPluginFactory) {
        return new ScriptingLanguage() {
            @Override
            public String getExtension() {
                return extension;
            }

            @Override
            public String getProvider() {
                return scriptPluginFactory;
            }
        };
    }

    private static ImmutableList<String> extensionsOf(List<ScriptingLanguage> scriptingLanguages) {
        ImmutableList.Builder<String> builder = ImmutableList.builder();
        for (ScriptingLanguage language : scriptingLanguages) {
            builder.add(language.getExtension());
        }
        return builder.build();
    }
}
