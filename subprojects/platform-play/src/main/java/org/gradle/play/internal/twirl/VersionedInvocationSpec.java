/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.play.internal.twirl;

import org.gradle.util.CollectionUtils;

import java.io.File;
import java.util.List;

class VersionedInvocationSpec {
    private final TwirlCompilerVersion version;
    private Class<?>[] parameterTypes;
    private List<Object> parameters;

    public VersionedInvocationSpec(TwirlCompilerVersion version, Object... parameters) {
        this.version = version;
        this.parameters = CollectionUtils.toList(parameters);
        this.parameterTypes = setupParameterTypes(parameters);
    }

    private Class<?>[] setupParameterTypes(Object[] parameters) {
        Class<?>[] types = new Class<?>[parameters.length + 1];
        types[0] = File.class;
        for (int i = 1; i <= parameters.length; i++){
            Class<?> parameterType = parameters[i - 1].getClass();
            types[i] = parameterType == Boolean.class ? boolean.class : parameterType;
        }
        return types;
    }

    public Class<?>[] getParameterTypes() {
        return parameterTypes;
    }

    public Object[] getParameter(File canonicalFile) {
        return CollectionUtils.flattenCollections(canonicalFile, parameters).toArray();
    }

    public TwirlCompilerVersion getVersion() {
        return version;
    }
}
