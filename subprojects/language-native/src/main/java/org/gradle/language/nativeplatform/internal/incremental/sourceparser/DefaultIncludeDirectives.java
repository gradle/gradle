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
package org.gradle.language.nativeplatform.internal.incremental.sourceparser;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Multimaps;
import org.gradle.api.specs.Spec;
import org.gradle.language.nativeplatform.internal.Include;
import org.gradle.language.nativeplatform.internal.IncludeDirectives;
import org.gradle.language.nativeplatform.internal.IncludeType;
import org.gradle.language.nativeplatform.internal.Macro;
import org.gradle.language.nativeplatform.internal.MacroFunction;
import org.gradle.util.CollectionUtils;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;

public class DefaultIncludeDirectives implements IncludeDirectives {
    private final ImmutableList<Include> allIncludes;
    private final ImmutableListMultimap<String, Macro> macros;
    private final ImmutableListMultimap<String, MacroFunction> macroFunctions;

    public static IncludeDirectives of(ImmutableList<Include> allIncludes, ImmutableList<Macro> macros, ImmutableList<MacroFunction> macroFunctions) {
        if (allIncludes.isEmpty() && macros.isEmpty() && macroFunctions.isEmpty()) {
            return EMPTY;
        }
        return new DefaultIncludeDirectives(allIncludes,
            Multimaps.index(macros, new Function<Macro, String>() {
                @Nullable
                @Override
                public String apply(@Nullable Macro input) {
                    return input.getName();
                }
            }),
            Multimaps.index(macroFunctions, new Function<MacroFunction, String>() {
                @Nullable
                @Override
                public String apply(@Nullable MacroFunction input) {
                    return input.getName();
                }
            }));
    }

    private DefaultIncludeDirectives(ImmutableList<Include> allIncludes, ImmutableListMultimap<String, Macro> macros, ImmutableListMultimap<String, MacroFunction> macroFunctions) {
        this.allIncludes = allIncludes;
        this.macros = macros;
        this.macroFunctions = macroFunctions;
    }

    @Override
    public List<Include> getQuotedIncludes() {
        return CollectionUtils.filter(allIncludes, new Spec<Include>() {
            @Override
            public boolean isSatisfiedBy(Include element) {
                return element.getType() == IncludeType.QUOTED;
            }
        });
    }

    @Override
    public List<Include> getSystemIncludes() {
        return CollectionUtils.filter(allIncludes, new Spec<Include>() {
            @Override
            public boolean isSatisfiedBy(Include element) {
                return element.getType() == IncludeType.SYSTEM;
            }
        });
    }

    @Override
    public List<Include> getMacroIncludes() {
        return CollectionUtils.filter(allIncludes, new Spec<Include>() {
            @Override
            public boolean isSatisfiedBy(Include element) {
                return element.getType() == IncludeType.MACRO;
            }
        });
    }

    @Override
    public List<Include> getAll() {
        return allIncludes;
    }

    @Override
    public List<Include> getIncludesOnly() {
        return CollectionUtils.filter(allIncludes, new Spec<Include>() {
            @Override
            public boolean isSatisfiedBy(Include element) {
                return !element.isImport();
            }
        });
    }

    @Override
    public Iterable<Macro> getMacros(String name) {
        return macros.get(name);
    }

    @Override
    public Collection<Macro> getAllMacros() {
        return macros.values();
    }

    @Override
    public Iterable<MacroFunction> getMacroFunctions(String name) {
        return macroFunctions.get(name);
    }

    @Override
    public Collection<MacroFunction> getAllMacroFunctions() {
        return macroFunctions.values();
    }

    @Override
    public boolean hasMacros() {
        return !macros.isEmpty();
    }

    @Override
    public boolean hasMacroFunctions() {
        return !macroFunctions.isEmpty();
    }

    @Override
    public IncludeDirectives discardImports() {
        return new DefaultIncludeDirectives(ImmutableList.copyOf(getIncludesOnly()), macros, macroFunctions);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DefaultIncludeDirectives that = (DefaultIncludeDirectives) o;

        return allIncludes.equals(that.allIncludes) && macros.equals(that.macros) && macroFunctions.equals(that.macroFunctions);
    }

    @Override
    public int hashCode() {
        return allIncludes.hashCode() ^ macros.hashCode() ^ macroFunctions.hashCode();
    }
}
