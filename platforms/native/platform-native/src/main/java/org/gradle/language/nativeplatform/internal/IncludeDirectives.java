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
package org.gradle.language.nativeplatform.internal;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * An immutable snapshot of the preprocessor directives from a source or header file.
 */
public interface IncludeDirectives {
    List<Include> getQuotedIncludes();
    List<Include> getSystemIncludes();
    List<Include> getMacroIncludes();
    List<Include> getAll();
    List<Include> getIncludesOnly();

    Iterable<Macro> getMacros(String name);
    Iterable<MacroFunction> getMacroFunctions(String name);

    Collection<Macro> getAllMacros();
    Collection<MacroFunction> getAllMacroFunctions();

    boolean hasMacros();
    boolean hasMacroFunctions();

    /**
     * Returns a copy of these directives, with #import directives removed.
     */
    IncludeDirectives discardImports();

    IncludeDirectives EMPTY = new IncludeDirectives() {
        @Override
        public List<Include> getQuotedIncludes() {
            return Collections.emptyList();
        }

        @Override
        public List<Include> getSystemIncludes() {
            return Collections.emptyList();
        }

        @Override
        public List<Include> getMacroIncludes() {
            return Collections.emptyList();
        }

        @Override
        public List<Include> getAll() {
            return Collections.emptyList();
        }

        @Override
        public List<Include> getIncludesOnly() {
            return Collections.emptyList();
        }

        @Override
        public Iterable<Macro> getMacros(String name) {
            return Collections.emptyList();
        }

        @Override
        public Iterable<MacroFunction> getMacroFunctions(String name) {
            return Collections.emptyList();
        }

        @Override
        public Collection<Macro> getAllMacros() {
            return Collections.emptyList();
        }

        @Override
        public Collection<MacroFunction> getAllMacroFunctions() {
            return Collections.emptyList();
        }

        @Override
        public boolean hasMacros() {
            return false;
        }

        @Override
        public boolean hasMacroFunctions() {
            return false;
        }

        @Override
        public IncludeDirectives discardImports() {
            return this;
        }
    };
}
