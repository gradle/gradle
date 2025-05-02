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

package org.gradle.nativeplatform.internal;

import org.gradle.nativeplatform.PreprocessingTool;

import java.util.LinkedHashMap;
import java.util.Map;

public class DefaultPreprocessingTool extends DefaultTool implements PreprocessingTool {
    private final Map<String, String> definitions = new LinkedHashMap<String, String>();

    @Override
    public Map<String, String> getMacros() {
        return definitions;
    }

    @Override
    public void define(String name) {
        definitions.put(name, null);
    }

    @Override
    public void define(String name, String definition) {
        definitions.put(name, definition);
    }
}
