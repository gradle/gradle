/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.buildinit.plugins.internal.modifiers;

public class Language {
    public static final Language NONE = Language.withName("none");
    public static final Language JAVA = Language.withName("Java");
    public static final Language GROOVY = Language.withName("Groovy");
    public static final Language KOTLIN = Language.withNameAndExtension("Kotlin", "kt");
    public static final Language SCALA = Language.withName("Scala");
    public static final Language CPP = Language.withName("C++", "cpp");
    public static final Language SWIFT = Language.withName("Swift", "swift");

    private final String displayName;
    private final String name;
    private final String extension;

    public static Language withName(String displayName) {
        return new Language(displayName.toLowerCase(), displayName, displayName.toLowerCase());
    }

    public static Language withName(String displayName, String name) {
        return new Language(name, displayName, name);
    }

    public static Language withNameAndExtension(String displayName, String extension) {
        return new Language(displayName.toLowerCase(), displayName, extension);
    }

    private Language(String name, String displayName, String extension) {
        this.name = name;
        this.displayName = displayName;
        this.extension = extension;
    }

    @Override
    public String toString() {
        return displayName;
    }

    public String getName() {
        return name;
    }

    public String getExtension() {
        return extension;
    }
}
