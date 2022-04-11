/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.api.internal.catalog;

import com.google.common.base.Splitter;
import org.gradle.api.initialization.ProjectDescriptor;

import java.io.IOException;
import java.io.Writer;
import java.util.stream.Collectors;

import static java.util.Comparator.comparing;

public class AbstractProjectAccessorsSourceGenerator extends AbstractSourceGenerator {
    public AbstractProjectAccessorsSourceGenerator(Writer writer) {
        super(writer);
    }

    protected static String toClassName(String path, String rootProjectName) {
        String name = toProjectName(path);
        if (name.isEmpty()) {
            name = toJavaName(rootProjectName);
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
        addImport("org.gradle.api.internal.catalog.DelegatingProjectDependency");
        addImport("org.gradle.api.internal.catalog.TypeSafeProjectDependencyFactory");
        addImport("javax.inject.Inject");
        writeLn();
    }

    protected static String rootProjectName(ProjectDescriptor descriptor) {
        ProjectDescriptor current = descriptor;
        while (current.getParent() != null) {
            current = current.getParent();
        }
        return current.getName();
    }

    protected void writeProjectAccessor(String name, ProjectDescriptor descriptor) throws IOException {
        writeLn("    /**");
        String path = descriptor.getPath();
        writeLn("     * Creates a project dependency on the project at path \"" + path + "\"");
        writeLn("     */");
        String returnType = toClassName(path, rootProjectName(descriptor));
        writeLn("    public " +  returnType + " get" + name + "() { return new " + returnType + "(getFactory(), create(\"" + path + "\")); }");
        writeLn();
    }

    protected void processChildren(ProjectDescriptor current) {
        current.getChildren()
            .stream()
            .sorted(comparing(ProjectDescriptor::getPath))
            .forEachOrdered(child -> {
                try {
                    writeProjectAccessor(toJavaName(child.getName()), child);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
    }
}
