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

package org.gradle.buildinit.plugins.internal;

public class Language {
    public static final Language JAVA = new Language("java");
    public static final Language GROOVY = new Language("groovy");
    public static final Language KOTLIN = new Language("kotlin", "kt");
    public static final Language SCALA = new Language("scala");
    public static final Language CPP = new Language("cpp");

    private final String name;
    private final String extension;

    public Language(String name) {
        this(name, name);
    }

    public Language(String name, String extension) {
        this.name = name;
        this.extension = extension;
    }

    public String getName() {
        return name;
    }

    public String getExtension() {
        return extension;
    }
}
