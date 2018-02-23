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

package org.gradle.language.nativeplatform.internal.incremental;

import org.gradle.language.nativeplatform.internal.IncludeDirectives;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CollectingMacroLookup implements MacroLookup {
    private final List<MacroSource> uncollected = new ArrayList<MacroSource>();
    private Map<File, IncludeDirectives> visible;

    /**
     * Appends a single file.
     */
    public void append(File file, IncludeDirectives includeDirectives) {
        if (includeDirectives.getMacros().isEmpty() && includeDirectives.getMacrosFunctions().isEmpty()) {
            // Ignore
            return;
        }
        if (visible == null) {
            visible = new LinkedHashMap<File, IncludeDirectives>();
            visible.put(file, includeDirectives);
        } else if (!visible.containsKey(file)) {
            visible.put(file, includeDirectives);
        }
    }

    /**
     * Appends a source of macros
     */
    public void append(MacroSource source) {
        uncollected.add(source);
    }

    @Override
    public Iterator<IncludeDirectives> iterator() {
        collectAll();
        if (visible == null) {
            return Collections.emptyIterator();
        }
        return visible.values().iterator();
    }

    public void appendTo(CollectingMacroLookup lookup) {
        collectAll();
        if (visible != null) {
            for (Map.Entry<File, IncludeDirectives> entry : visible.entrySet()) {
                lookup.append(entry.getKey(), entry.getValue());
            }
        }
    }

    private void collectAll() {
        while (!uncollected.isEmpty()) {
            MacroSource source = uncollected.remove(0);
            source.collectInto(this);
        }
    }

    interface MacroSource {
        void collectInto(CollectingMacroLookup lookup);
    }
}
