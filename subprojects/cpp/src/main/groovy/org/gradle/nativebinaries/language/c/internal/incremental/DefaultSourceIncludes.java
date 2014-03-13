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
package org.gradle.nativebinaries.language.c.internal.incremental;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

class DefaultSourceIncludes implements SourceIncludes, Serializable {
    private final List<String> quotedIncludes = new ArrayList<String>();
    private final List<String> systemIncludes = new ArrayList<String>();
    private final List<String> macroIncludes = new ArrayList<String>();

    public void addAll(List<String> includes) {
        for (String value : includes) {
            if (value.startsWith("<") && value.endsWith(">")) {
                systemIncludes.add(strip(value));
            } else if (value.startsWith("\"") && value.endsWith("\"")) {
                quotedIncludes.add(strip(value));
            } else {
                macroIncludes.add(value);
            }
        }
    }

    private String strip(String include) {
        return include.substring(1, include.length() - 1);
    }

    public List<String> getQuotedIncludes() {
        return quotedIncludes;
    }

    public List<String> getSystemIncludes() {
        return systemIncludes;
    }

    public List<String> getMacroIncludes() {
        return macroIncludes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DefaultSourceIncludes)) {
            return false;
        }

        DefaultSourceIncludes that = (DefaultSourceIncludes) o;

        return macroIncludes.equals(that.macroIncludes)
                && quotedIncludes.equals(that.quotedIncludes)
                && systemIncludes.equals(that.systemIncludes);

    }

    @Override
    public int hashCode() {
        int result = quotedIncludes.hashCode();
        result = 31 * result + systemIncludes.hashCode();
        result = 31 * result + macroIncludes.hashCode();
        return result;
    }
}
