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

public class RootProjectAccessorSourceGenerator extends AbstractProjectAccessorsSourceGenerator {

    public static final String ROOT_PROJECT_ACCESSOR_CLASSNAME = "RootProjectAccessor";

    public RootProjectAccessorSourceGenerator(Writer writer) {
        super(writer);
    }

    public static void generateSource(Writer writer,
                                      ProjectDescriptor root,
                                      String packageName) {
        RootProjectAccessorSourceGenerator generator = new RootProjectAccessorSourceGenerator(writer);
        try {
            generator.generate(packageName, ROOT_PROJECT_ACCESSOR_CLASSNAME, root);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void generate(String packageName, String className, ProjectDescriptor current) throws IOException {
        writeHeader(packageName);
        writeLn("@NonNullApi");
        writeLn("public class " + className + " extends TypeSafeProjectDependencyFactory {\n");
        writeLn();
        writeLn("    @Inject");
        writeLn("    public " + className + "(DefaultProjectDependencyFactory factory, ProjectFinder finder) {");
        writeLn("        super(factory, finder);");
        writeLn("    }");
        writeLn();
        writeProjectAccessor(toJavaName(rootProjectName(current)), current);
        processChildren(current);

        writeLn("}");
    }

}
