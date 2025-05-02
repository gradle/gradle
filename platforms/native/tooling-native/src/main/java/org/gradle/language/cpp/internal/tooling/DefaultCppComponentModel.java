/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.language.cpp.internal.tooling;

import java.io.Serializable;
import java.util.List;

public class DefaultCppComponentModel implements Serializable {
    private final String name;
    private final String baseName;
    private final List<DefaultCppBinaryModel> binaries;

    public DefaultCppComponentModel(String name, String baseName, List<DefaultCppBinaryModel> binaries) {
        this.name = name;
        this.baseName = baseName;
        this.binaries = binaries;
    }

    public String getName() {
        return name;
    }

    public String getBaseName() {
        return baseName;
    }

    public List<DefaultCppBinaryModel> getBinaries() {
        return binaries;
    }
}
