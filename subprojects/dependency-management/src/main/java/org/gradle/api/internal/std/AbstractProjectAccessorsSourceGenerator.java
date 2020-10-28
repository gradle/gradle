/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.api.internal.std;

import com.google.common.base.Splitter;
import org.gradle.api.initialization.ProjectDescriptor;

import java.io.IOException;
import java.io.Writer;
import java.util.stream.Collectors;

public class AbstractProjectAccessorsSourceGenerator extends AbstractSourceGenerator {
    public AbstractProjectAccessorsSourceGenerator(Writer writer) {
        super(writer);
    }

    protected static String toClassName(String path) {
        String name = toProjectName(path);
        if (name.isEmpty()) {
            name = "Root";
        }
        return name + "ProjectDependency";
    }

    protected static String toProjectName(String path) {
        return Splitter.on(":")
            .omitEmptyStrings()
            .splitToList(path)
            .stream()
            .map(AbstractProjectAccessorsSourceGenerator::toJavaName)
            .collect(Collectors.joining("_"));
    }

    protected void writeHeader(String packageName) throws IOException {
        writeLn("package " + packageName + ";");
        writeLn();
        addImport("org.gradle.api.NonNullApi");
        addImport("org.gradle.api.artifacts.ProjectDependency");
        addImport("org.gradle.api.internal.artifacts.dependencies.ProjectDependencyInternal");
        addImport("org.gradle.api.internal.artifacts.DefaultProjectDependencyFactory");
        addImport("org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder");
        addImport("org.gradle.api.internal.std.DelegatingProjectDependency");
        addImport("org.gradle.api.internal.std.TypeSafeProjectDependencyFactory");
        addImport("javax.inject.Inject");
        writeLn();
    }

    protected void writeProjectAccessor(String name, ProjectDescriptor descriptor) throws IOException {
        writeLn("    /**");
        writeLn("     * Creates a project dependency on the project at path " + descriptor);
        writeLn("     */");
        String returnType = toClassName(descriptor.getPath());
        writeLn("    public " +  returnType + " get" + name + "() { return new " + returnType + "(getFactory(), create(\"" + descriptor.getPath() + "\")); }");
        writeLn();
    }
}
