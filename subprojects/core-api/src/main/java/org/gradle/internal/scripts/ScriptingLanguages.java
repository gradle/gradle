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

import org.gradle.scripts.ScriptingLanguage;

import javax.annotation.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Registry of scripting languages.
 */
public final class ScriptingLanguages {

    public static final ScriptingLanguage GROOVY =
        scriptingLanguage("Groovy", ".gradle", null);

    public static final ScriptingLanguage KOTLIN =
        scriptingLanguage("Kotling", ".gradle.kts", "org.gradle.kotlin.dsl.provider.KotlinScriptPluginFactory");

    public static final ScriptingLanguage DECLARATIVE =
        scriptingLanguage("Declarative", ".gradle.dcl", "org.gradle.internal.declarativedsl.provider.DeclarativeDslScriptPluginFactory");

    private static final List<ScriptingLanguage> ALL =
        Collections.unmodifiableList(Arrays.asList(GROOVY, KOTLIN, DECLARATIVE));

    public static List<ScriptingLanguage> all() {
        return ALL;
    }

    private static ScriptingLanguage scriptingLanguage(String name, String extension, @Nullable String scriptPluginFactory) {
        return new ScriptingLanguage() {
            @Override
            public String getExtension() {
                return extension;
            }

            @Override
            public String getProvider() {
                return scriptPluginFactory;
            }

            @Override
            public String toString() {
                return name;
            }

            @Override
            public int hashCode() {
                return extension.hashCode();
            }

            @Override
            public boolean equals(Object obj) {
                if (obj == this) {
                    return true;
                }
                if (obj instanceof ScriptingLanguage) {
                    ScriptingLanguage other = (ScriptingLanguage) obj;
                    return extension.equals(other.getExtension());
                }
                return false;
            }
        };
    }
}
