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
import com.google.common.collect.ImmutableList;
import org.gradle.api.InvalidUserDataException;
import org.gradle.internal.logging.text.TreeFormatter;
import org.gradle.util.TextUtil;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.List;
import java.util.stream.Collectors;

public class DependenciesSourceGenerator extends AbstractSourceGenerator {

    private static final int MAX_ENTRIES = 30000;
    private final DefaultVersionCatalog config;

    public DependenciesSourceGenerator(Writer writer,
                                       DefaultVersionCatalog config) {
        super(writer);
        this.config = config;
    }

    public static void generateSource(Writer writer,
                                      DefaultVersionCatalog config,
                                      String packageName,
                                      String className) {
        DependenciesSourceGenerator generator = new DependenciesSourceGenerator(writer, config);
        try {
            generator.generate(packageName, className);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void generate(String packageName, String className) throws IOException {
        writeLn("package " + packageName + ";");
        writeLn();
        addImport("org.gradle.api.NonNullApi");
        addImport("org.gradle.api.artifacts.MinimalExternalModuleDependency");
        addImport("org.gradle.api.artifacts.ExternalModuleDependencyBundle");
        addImport("org.gradle.api.artifacts.MutableVersionConstraint");
        addImport("org.gradle.api.provider.Provider");
        addImport("org.gradle.api.provider.ProviderFactory");
        addImport("org.gradle.api.internal.std.AbstractExternalDependencyFactory");
        addImport("org.gradle.api.internal.std.DefaultVersionCatalog");
        addImport("java.util.Map");
        addImport("javax.inject.Inject");
        writeLn();
        String description = TextUtil.normaliseLineSeparators(config.getDescription());
        writeLn("/**");
        for (String descLine : Splitter.on('\n').split(description)) {
            writeLn(" * " + descLine);
        }
        String versionsClassName = className + "Versions";
        String bundlesClassName = className + "Bundles";
        writeLn("*/");
        writeLn("@NonNullApi");
        writeLn("public class " + className + " extends AbstractExternalDependencyFactory {");
        writeLn();
        writeLn("    private final " + versionsClassName + " versions = new " + versionsClassName + "();");
        writeLn("    private final " + bundlesClassName + " bundles = new " + bundlesClassName + "();");
        writeLn();
        writeLn("    @Inject");
        writeLn("    public " + className + "(DefaultVersionCatalog config, ProviderFactory providers) {");
        writeLn("        super(config, providers);");
        writeLn("    }");
        writeLn();
        List<String> dependencies = config.getDependencyAliases();
        List<String> bundles = config.getBundleAliases();
        List<String> versions = config.getVersionAliases();
        performValidation(dependencies, bundles, versions);
        writeDependencyAccessors(dependencies);
        writeBundleAccessors(bundlesClassName, bundles);
        writeVersionAccessors(versionsClassName, versions);
        writeLn("}");
    }

    private void writeBundleAccessors(String bundlesClassName, List<String> bundles) throws IOException {
        writeLn("    /**");
        writeLn("    * Returns the available bundles for this model.");
        writeLn("    */");
        writeLn("    public " + bundlesClassName + " getBundles() { return bundles; }");
        writeLn();
        writeLn("    public class " + bundlesClassName + " extends BundleFactory {");
        for (String alias : bundles) {
            BundleModel bundle = config.getBundle(alias);
            List<String> coordinates = bundle.getComponents().stream()
                .map(config::getDependencyData)
                .map(this::coordinatesDescriptorFor)
                .collect(Collectors.toList());
            writeBundle(alias, coordinates, bundle.getContext());
        }
        writeLn("    }");
        writeLn();
    }

    private void writeDependencyAccessors(List<String> dependencies) throws IOException {
        for (String alias : dependencies) {
            DependencyModel model = config.getDependencyData(alias);
            String coordinates = coordinatesDescriptorFor(model);
            writeAccessor(alias, coordinates, model.getContext());
        }
    }

    private void writeVersionAccessors(String versionsClassName, List<String> versions) throws IOException {
        writeLn("    /**");
        writeLn("    * Returns the available versions for this model.");
        writeLn("    */");
        writeLn("    public " + versionsClassName + " getVersions() { return versions; }");
        writeLn();
        writeLn("    public class " + versionsClassName + " extends VersionFactory {");
        for (String version : versions) {
            VersionModel vm = config.getVersion(version);
            String context = vm.getContext();
            writeLn("        /**");
            writeLn("         * Returns the version associated to this alias: " + version);
            writeLn("         * If the version is a rich version and that its not expressable as a");
            writeLn("         * single version string, then an empty string is returned.");
            if (context != null) {
                writeLn("         * This version was declared in " + context);
            }
            writeLn("         */");
            writeLn("        public Provider<String> get" + toJavaName(version) + "() { return getVersion(\"" + version + "\"); }");
            writeLn();
        }
        writeLn("    }");
        writeLn();
    }

    private static void performValidation(List<String> dependencies, List<String> bundles, List<String> versions) {
        assertDependencyAliases(dependencies);
        assertUnique(dependencies, "dependency aliases", "");
        assertUnique(bundles, "dependency bundles", "Bundle");
        assertUnique(versions, "dependency versions", "Version");
        int size = dependencies.size() + bundles.size() + versions.size();
        if (size > MAX_ENTRIES) {
            maybeThrowValidationError(ImmutableList.of("model contains too many entries (" + size + "), maximum is " + MAX_ENTRIES));
        }
    }

    private static void assertDependencyAliases(List<String> names) {
        List<String> errors = names.stream()
            .filter(n -> n.toLowerCase().endsWith("bundle") || n.toLowerCase().endsWith("version"))
            .map(n -> "alias " + n + " isn't a valid: it shouldn't end with 'Bundle' or 'Version'")
            .collect(Collectors.toList());
        maybeThrowValidationError(errors);
    }

    private static void assertUnique(List<String> names, String prefix, String suffix) {
        List<String> errors = names.stream()
            .collect(Collectors.groupingBy(AbstractSourceGenerator::toJavaName))
            .entrySet()
            .stream()
            .filter(e -> e.getValue().size() > 1)
            .map(e -> prefix + " " + e.getValue().stream().sorted().collect(Collectors.joining(" and ")) + " are mapped to the same accessor name get" + e.getKey() + suffix + "()")
            .collect(Collectors.toList());
        maybeThrowValidationError(errors);
    }

    private static void maybeThrowValidationError(List<String> errors) {
        if (errors.size() == 1) {
            throw new InvalidUserDataException("Cannot generate dependency accessors because " + errors.get(0));
        }
        if (errors.size() > 1) {
            TreeFormatter formatter = new TreeFormatter();
            formatter.node("Cannot generate dependency accessors because");
            formatter.startChildren();
            errors.stream()
                .sorted()
                .forEach(formatter::node);
            formatter.endChildren();
            throw new InvalidUserDataException(formatter.toString());
        }
    }

    private String coordinatesDescriptorFor(DependencyModel dependencyData) {
        return dependencyData.getGroup() + ":" + dependencyData.getName();
    }

    private void writeAccessor(String alias, String coordinates, @Nullable String context) throws IOException {
        writeLn("    /**");
        writeLn("     * Creates a dependency provider for " + alias + " (" + coordinates + ")");
        if (context != null) {
            writeLn("     * This dependency was declared in " + context);
        }
        writeLn("     */");
        writeLn("    public Provider<MinimalExternalModuleDependency> get" + toJavaName(alias) + "() { return create(\"" + alias + "\"); }");
        writeLn();
    }

    private void writeBundle(String alias, List<String> coordinates, @Nullable String context) throws IOException {
        writeLn("        /**");
        writeLn("         * Creates a dependency bundle provider for " + alias + " which is an aggregate for the following dependencies:");
        writeLn("         * <ul>");
        for (String coordinate : coordinates) {
            writeLn("         *    <li>" + coordinate + "</li>");
        }
        writeLn("         * </ul>");
        if (context != null) {
            writeLn("         * This bundle was declared in " + context);
        }
        writeLn("         */");
        writeLn("        public Provider<ExternalModuleDependencyBundle> get" + toJavaName(alias) + "() { return createBundle(\"" + alias + "\"); }");
        writeLn();
    }

}
