/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.notations.parsers;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.internal.notations.api.NotationParser;
import org.gradle.api.internal.notations.api.UnsupportedNotationException;
import org.gradle.util.ConfigureUtil;

import java.util.*;

public abstract class MapNotationParser<T> implements NotationParser<T> {
    public void describe(Collection<String> candidateFormats) {
        candidateFormats.add("Maps");
    }

    protected Collection<String> getRequiredKeys() {
        return Collections.emptyList();
    }
    
    protected Collection<String> getOptionalKeys() {
        return Collections.emptyList();
    }

    /**
     * Parses the given Map.
     */
    protected abstract T parseMap(Map<String, Object> values);
    
    public T parseNotation(Object notation) throws UnsupportedNotationException {
        if (!(notation instanceof Map)) {
            throw new UnsupportedNotationException(notation);
        }
        Map<String, Object> values = new HashMap<String, Object>((Map<String, ?>) notation);
        Set<String> missing = new TreeSet<String>(getRequiredKeys());
        missing.removeAll(values.keySet());
        if (!missing.isEmpty()) {
            throw new InvalidUserDataException(String.format("Required keys %s are missing from map %s.", missing, values));
        }

        T result = parseMap(Collections.unmodifiableMap(values));
        values.keySet().removeAll(getRequiredKeys());
        values.keySet().removeAll(getOptionalKeys());
        ConfigureUtil.configureByMap(values, result);
        return result;
    }

    protected String get(Map<String, Object> args, String key) {
        Object value = args.get(key);
        return value != null ? value.toString() : null;
    }
}
