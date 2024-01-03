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

import org.gradle.api.initialization.ProjectDescriptor;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;

public class ProjectAccessorsSourceGenerator extends AbstractProjectAccessorsSourceGenerator {

    public ProjectAccessorsSourceGenerator(Writer writer) {
        super(writer);
    }

    public static String generateSource(Writer writer,
                                        ProjectDescriptor current,
                                        String packageName) {
        ProjectAccessorsSourceGenerator generator = new ProjectAccessorsSourceGenerator(writer);
        try {
            String className = toClassName(current.getPath(), rootProjectName(current));
            generator.generate(packageName, className, current);
            return className;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void generate(String packageName, String className, ProjectDescriptor current) throws IOException {
        writeHeader(packageName);
        writeLn("@NonNullApi");
        writeLn("public class " + className + " extends DelegatingProjectDependency {");
        writeLn();
        writeLn("    @Inject");
        writeLn("    public " + className + "(TypeSafeProjectDependencyFactory factory, ProjectDependencyInternal delegate) {");
        writeLn("        super(factory, delegate);");
        writeLn("    }");
        writeLn();
        processChildren(current);
        writeLn("}");
    }

}
