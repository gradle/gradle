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

import org.gradle.api.Project;
import org.gradle.language.cpp.CppApplication;
import org.gradle.language.cpp.CppLibrary;
import org.gradle.tooling.model.cpp.CppComponentType;
import org.gradle.tooling.provider.model.ToolingModelBuilder;

import java.io.Serializable;

public class CppModelBuilder implements ToolingModelBuilder {
    @Override
    public boolean canBuild(String modelName) {
        return modelName.equals("org.gradle.tooling.model.cpp.CppComponent");
    }

    @Override
    public Object buildAll(String modelName, Project project) {
        CppApplication application = project.getComponents().withType(CppApplication.class).findByName("main");
        if (application != null) {
            return new DefaultCppComponent(CppComponentType.APPLICATION);
        }
        CppLibrary library = project.getComponents().withType(CppLibrary.class).findByName("main");
        if (library != null) {
            return new DefaultCppComponent(CppComponentType.LIBRARY);
        }
        return null;
    }

    public static class DefaultCppComponent implements Serializable {
        private final CppComponentType type;

        public DefaultCppComponent(CppComponentType type) {
            this.type = type;
        }

        public CppComponentType getComponentType() {
            return type;
        }
    }
}
