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

package org.gradle.api.internal.plugins;

import groovy.lang.Closure;
import org.gradle.api.GradleException;
import org.gradle.util.ConfigureUtil;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author: Szczepan Faber, created at: 6/24/11
 */
public class ExtensionsStorage {

    private final Map<String, Object> extensions = new LinkedHashMap<String, Object>();

    public void add(String name, Object extension) {
        extensions.put(name, extension);
    }

    public boolean hasExtension(String name) {
        return extensions.containsKey(name);
    }

    public Map<String, Object> getAsMap() {
        return extensions;
    }

    public Object getExtension(String name) {
        return extensions.get(name);
    }

    public void checkExtensionIsNotReassigned(String name) {
        if (hasExtension(name)) {
            throw new GradleException("There's an extension registered with name '%s'. You should not reassign it via a property setter.");
        }
    }

    public boolean isConfigureExtensionMethod(String methodName, Object ... arguments) {
        return extensions.containsKey(methodName) && arguments.length == 1 && arguments[0] instanceof Closure;
    }

    public Object configureExtension(String methodName, Object ... arguments) {
        return ConfigureUtil.configure((Closure) arguments[0], extensions.get(methodName));
    }
}