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
import org.apache.commons.lang.StringUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class DependenciesSourceGenerator {
    private static final Pattern SEPARATOR_PATTERN = Pattern.compile("[.-]");

    private final Writer writer;
    private final DependenciesConfig config;
    private final String ln = System.getProperty("line.separator", "\n");

    public DependenciesSourceGenerator(Writer writer,
                                       DependenciesConfig config) {
        this.writer = writer;
        this.config = config;
    }

    public static void generateSource(Writer writer,
                                      DependenciesConfig config,
                                      String packageName,
                                      String className) {
        DependenciesSourceGenerator generator = new DependenciesSourceGenerator(writer, config);
        try {
            generator.generate(packageName, className);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void addImport(String clazz) throws IOException {
        writeLn("import " + clazz + ";");
    }

    private void generate(String packageName, String className) throws IOException {
        writeLn("package " + packageName + ";");
        writeLn();
        addImport("org.gradle.api.artifacts.MinimalExternalModuleDependency");
        addImport("org.gradle.api.artifacts.ExternalModuleDependencyBundle");
        addImport("org.gradle.api.artifacts.MutableVersionConstraint");
        addImport("org.gradle.api.provider.Provider");
        addImport("org.gradle.api.provider.ProviderFactory");
        addImport("org.gradle.api.internal.std.AbstractExternalDependencyFactory");
        addImport("org.gradle.api.internal.std.DependenciesConfig");
        addImport("java.util.Map");
        addImport("javax.inject.Inject");
        writeLn();
        writeLn("public class " + className + " extends AbstractExternalDependencyFactory {");
        writeLn();
        writeLn("    @Inject");
        writeLn("    public " + className + "(DependenciesConfig config, ProviderFactory providers) {");
        writeLn("        super(config, providers);");
        writeLn("    }");
        writeLn();
        List<String> dependencies = config.getDependencyAliases();
        List<String> bundles = config.getBundleAliases();
        for (String alias : dependencies) {
            String coordinates = coordinatesDescriptorFor(config.getDependencyData(alias));
            writeAccessor(alias, coordinates);
            writeGetVersion(alias, coordinates);
        }
        for (String alias : bundles) {
            List<String> coordinates = config.getBundle(alias).stream()
                .map(config::getDependencyData)
                .map(this::coordinatesDescriptorFor)
                .collect(Collectors.toList());
            writeBundle(alias, coordinates);
        }
        writeLn("}");
    }

    private String coordinatesDescriptorFor(DependencyData dependencyData) {
        return dependencyData.getGroup() + ":" + dependencyData.getName();
    }

    private void writeAccessor(String alias, String coordinates) throws IOException {
        writeLn("    /**");
        writeLn("     * Creates a dependency provider for " + alias + " (" + coordinates + ")");
        writeLn("     */");
        writeLn("    public Provider<MinimalExternalModuleDependency> get" + toMethodName(alias) + "() { return create(\"" + alias + "\"); }");
        writeLn();
    }

    private void writeGetVersion(String alias, String coordinates) throws IOException {
        writeLn("    /**");
        writeLn("     * Creates a version constraint provider for " + alias + " (" + coordinates + ")");
        writeLn("     */");
        writeLn("    public Provider<MutableVersionConstraint> get" + toMethodName(alias) + "Version() { return createVersion(\"" + alias + "\"); }");
        writeLn();
    }

    private void writeBundle(String alias, List<String> coordinates) throws IOException {
        writeLn("    /**");
        writeLn("     * Creates a dependency bundle provider for " + alias + " which is an aggregate for the following dependencies:");
        writeLn("     * <ul>");
        for (String coordinate : coordinates) {
            writeLn("     * <li>" + coordinate + "</li>");
        }
        writeLn("     * </ul>");
        writeLn("     */");
        writeLn("    public Provider<ExternalModuleDependencyBundle> get" + toMethodName(alias) + "() { return createBundle(\"" + alias + "\"); }");
        writeLn();
    }

    private static String toMethodName(String alias) {
        return Splitter.on(SEPARATOR_PATTERN)
            .splitToList(alias)
            .stream()
            .map(StringUtils::capitalize)
            .collect(Collectors.joining());
    }

    private void writeLn() throws IOException {
        writer.write(ln);
    }

    public void writeLn(String source) throws IOException {
        writer.write(source + ln);
    }
}
