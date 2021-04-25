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
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.NonNullApi;
import org.gradle.api.artifacts.ExternalModuleDependencyBundle;
import org.gradle.api.artifacts.MinimalExternalModuleDependency;
import org.gradle.api.artifacts.MutableVersionConstraint;
import org.gradle.api.internal.catalog.problems.VersionCatalogProblem;
import org.gradle.api.internal.catalog.problems.VersionCatalogProblemId;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.util.internal.TextUtil;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.google.common.collect.ImmutableList.of;
import static org.gradle.api.internal.catalog.problems.DefaultCatalogProblemBuilder.buildProblem;
import static org.gradle.api.internal.catalog.problems.DefaultCatalogProblemBuilder.maybeThrowError;

public class LibrariesSourceGenerator extends AbstractSourceGenerator {

    private static final int MAX_ENTRIES = 30000;
    public static final String ERROR_HEADER = "Cannot generate dependency accessors";
    private final DefaultVersionCatalog config;

    public LibrariesSourceGenerator(Writer writer,
                                    DefaultVersionCatalog config) {
        super(writer);
        this.config = config;
    }

    public static void generateSource(Writer writer,
                                      DefaultVersionCatalog config,
                                      String packageName,
                                      String className) {
        LibrariesSourceGenerator generator = new LibrariesSourceGenerator(writer, config);
        try {
            generator.generate(packageName, className);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void generate(String packageName, String className) throws IOException {
        writeLn("package " + packageName + ";");
        writeLn();
        addImports();
        writeLn();
        String description = TextUtil.normaliseLineSeparators(config.getDescription());
        writeLn("/**");
        for (String descLine : Splitter.on('\n').split(description)) {
            writeLn(" * " + descLine);
        }
        List<String> libraries = config.getDependencyAliases();
        List<String> bundles = config.getBundleAliases();
        List<String> versions = config.getVersionAliases();
        performValidation(libraries, bundles, versions);
        ClassNode librariesEntryPoint = toClassNode(libraries, rootNode(AccessorKind.library));
        ClassNode versionsEntryPoint = toClassNode(versions, rootNode(AccessorKind.version, "versions")).parent;
        ClassNode bundlesEntryPoint = toClassNode(bundles, rootNode(AccessorKind.bundle, "bundles")).parent;
        writeLibraryEntryPoint(className, librariesEntryPoint, versionsEntryPoint, bundlesEntryPoint);
    }

    private void writeLibraryEntryPoint(String className, ClassNode librariesEntryPoint, ClassNode versionsEntryPoint, ClassNode bundlesEntryPoint) throws IOException {
        writeLn("*/");
        writeLn("@NonNullApi");
        writeLn("public class " + className + " extends AbstractExternalDependencyFactory {");
        writeLn();
        indent(() -> {
            writeLn("private final AbstractExternalDependencyFactory owner = this;");
            writeSubAccessorFieldsOf(librariesEntryPoint, AccessorKind.library);
            writeSubAccessorFieldsOf(versionsEntryPoint, AccessorKind.version);
            writeSubAccessorFieldsOf(bundlesEntryPoint, AccessorKind.bundle);
            writeLn();
            writeLn("@Inject");
            writeLn("public " + className + "(DefaultVersionCatalog config, ProviderFactory providers) {");
            writeLn("    super(config, providers);");
            writeLn("}");
            writeLn();
            writeLibraryAccessors(librariesEntryPoint);
            writeVersionAccessors(versionsEntryPoint);
            writeBundleAccessors(bundlesEntryPoint);
            writeLibrarySubClasses(librariesEntryPoint);
            writeVersionSubClasses(versionsEntryPoint);
            writeBundleSubClasses(bundlesEntryPoint);
        });
        writeLn("}");
    }

    private void addImports() throws IOException {
        addImport(NonNullApi.class);
        addImport(MinimalExternalModuleDependency.class);
        addImport(ExternalModuleDependencyBundle.class);
        addImport(MutableVersionConstraint.class);
        addImport(Provider.class);
        addImport(ProviderFactory.class);
        addImport(AbstractExternalDependencyFactory.class);
        addImport(DefaultVersionCatalog.class);
        addImport(Map.class);
        addImport(Inject.class);
    }

    private void writeLibrarySubClasses(ClassNode classNode) throws IOException {
        for (ClassNode child : classNode.getChildren()) {
            writeLibraryAccessorClass(child);
            writeLibrarySubClasses(child);
        }
    }

    private void writeVersionSubClasses(ClassNode classNode) throws IOException {
        for (ClassNode child : classNode.getChildren()) {
            writeVersionAccessorClass(child);
            writeVersionSubClasses(child);
        }
    }

    private void writeBundleSubClasses(ClassNode classNode) throws IOException {
        for (ClassNode child : classNode.getChildren()) {
            writeBundleAccessorClass(child);
            writeBundleSubClasses(child);
        }
    }

    private void writeBundleAccessorClass(ClassNode classNode) throws IOException {
        classNode.validate();
        String bundleClassName = classNode.getClassName();
        List<String> aliases = classNode.aliases
            .stream()
            .sorted()
            .collect(Collectors.toList());
        writeLn("public static class " + bundleClassName + " extends BundleFactory {");
        indent(() -> {
            writeSubAccessorFieldsOf(classNode, AccessorKind.bundle);
            writeLn();
            writeLn("public " + bundleClassName + "(ProviderFactory providers, DefaultVersionCatalog config) { super(providers, config); }");
            writeLn();
            for (String alias : aliases) {
                BundleModel bundle = config.getBundle(alias);
                List<String> coordinates = bundle.getComponents().stream()
                    .map(config::getDependencyData)
                    .map(this::coordinatesDescriptorFor)
                    .collect(Collectors.toList());
                writeBundle(alias, coordinates, bundle.getContext());
            }
            for (ClassNode child : classNode.getChildren()) {
                writeSubAccessor(child, AccessorKind.bundle);
            }
        });
        writeLn("}");
        writeLn();
    }

    private void writeLibraryAccessors(ClassNode classNode) throws IOException {
        classNode.validate();
        Set<String> dependencies = classNode.aliases;
        for (String alias : dependencies) {
            DependencyModel model = config.getDependencyData(alias);
            String coordinates = coordinatesDescriptorFor(model);
            writeDependencyAccessor(alias, coordinates, model.getContext());
        }
        for (ClassNode child : classNode.getChildren()) {
            writeSubAccessor(child, AccessorKind.library);
        }
    }

    private void writeVersionAccessors(ClassNode classNode) throws IOException {
        classNode.validate();
        Set<String> versionsAliases = classNode.aliases;
        for (String alias : versionsAliases) {
            VersionModel model = config.getVersion(alias);
            writeSingleVersionAccessor(alias, model.getContext(), model.getVersion().getDisplayName());
        }
        for (ClassNode child : classNode.getChildren()) {
            writeSubAccessor(child, AccessorKind.version);
        }
    }

    private void writeBundleAccessors(ClassNode classNode) throws IOException {
        classNode.validate();
        Set<String> versionsAliases = classNode.aliases;
        for (String alias : versionsAliases) {
            BundleModel model = config.getBundle(alias);
            List<String> coordinates = model.getComponents().stream()
                .map(config::getDependencyData)
                .map(this::coordinatesDescriptorFor)
                .collect(Collectors.toList());
            writeBundle(alias, coordinates, model.getContext());
        }
        for (ClassNode child : classNode.getChildren()) {
            writeSubAccessor(child, AccessorKind.bundle);
        }
    }

    private void writeSubAccessorFieldFor(ClassNode classNode, AccessorKind kind) throws IOException {
        String className = classNode.getClassName();
        writeLn("private final " + className + " " + kind.accessorVariableNameFor(className) + " = new " + className + "(" + kind.getConstructorParams() + ");");
    }

    private void writeSubAccessorFieldsOf(ClassNode classNode, AccessorKind kind) throws IOException {
        for (ClassNode child : classNode.getChildren()) {
            writeSubAccessorFieldFor(child, kind);
        }
    }

    private void writeLibraryAccessorClass(ClassNode classNode) throws IOException {
        classNode.validate();
        writeLn("public static class " + classNode.getClassName() + " extends SubDependencyFactory {");
        indent(() -> {
            writeSubAccessorFieldsOf(classNode, AccessorKind.library);
            writeLn();
            writeLn("public " + classNode.getClassName() + "(AbstractExternalDependencyFactory owner) { super(owner); }");
            writeLn();
            for (String alias : classNode.aliases) {
                DependencyModel model = config.getDependencyData(alias);
                String coordinates = coordinatesDescriptorFor(model);
                writeDependencyAccessor(alias, coordinates, model.getContext());
            }
            for (ClassNode child : classNode.getChildren()) {
                writeSubAccessor(child, AccessorKind.library);
            }
        });
        writeLn("}");
        writeLn();
    }

    private void writeVersionAccessorClass(ClassNode classNode) throws IOException {
        classNode.validate();
        String versionsClassName = classNode.getClassName();
        Set<String> versionAliases = classNode.getAliases();
        writeLn("public static class " + versionsClassName + " extends VersionFactory {");
        writeLn();
        indent(() -> {
            writeSubAccessorFieldsOf(classNode, AccessorKind.version);
            writeLn("public " + versionsClassName + "(ProviderFactory providers, DefaultVersionCatalog config) { super(providers, config); }");
            writeLn();
            for (String alias : versionAliases) {
                VersionModel vm = config.getVersion(alias);
                String context = vm.getContext();
                indent(() -> writeSingleVersionAccessor(alias, context, vm.getVersion().getDisplayName()));
            }
            for (ClassNode child : classNode.getChildren()) {
                writeSubAccessor(child, AccessorKind.version);
            }
        });
        writeLn("}");
        writeLn();
    }

    private void writeSingleVersionAccessor(String versionAlias, @Nullable String context, String version) throws IOException {
        writeLn("/**");
        writeLn(" * Returns the version associated to this alias: " + versionAlias + " (" + version + ")");
        writeLn(" * If the version is a rich version and that its not expressible as a");
        writeLn(" * single version string, then an empty string is returned.");
        if (context != null) {
            writeLn(" * This version was declared in " + context);
        }
        writeLn(" */");
        writeLn("public Provider<String> get" + toJavaName(leafNodeForAlias(versionAlias)) + "() { return getVersion(\"" + versionAlias + "\"); }");
        writeLn();
    }

    private String standardErrorLocation() {
        return "Version catalog " + config.getName();
    }

    private void performValidation(List<String> dependencies, List<String> bundles, List<String> versions) {
        assertUnique(dependencies, "dependency aliases", "");
        assertUnique(bundles, "dependency bundles", "Bundle");
        assertUnique(versions, "dependency versions", "Version");
        int size = dependencies.size() + bundles.size() + versions.size();
        if (size > MAX_ENTRIES) {
            maybeThrowError(ERROR_HEADER, of(buildProblem(VersionCatalogProblemId.TOO_MANY_ENTRIES, spec ->
                spec.inContext(this::standardErrorLocation)
                    .withShortDescription(() -> "Version catalog model contains too many entries (" + size + ")")
                    .happensBecause(() -> "The maximum number of aliases in a catalog is " + MAX_ENTRIES)
                    .addSolution(() -> "Reduce the number of aliases defined in this catalog")
                    .addSolution(() -> "Split the catalog into multiple catalogs")
                    .documented()
            )));
        }
    }

    private void assertUnique(List<String> names, String prefix, String suffix) {
        List<VersionCatalogProblem> errors = names.stream()
            .collect(Collectors.groupingBy(AbstractSourceGenerator::toJavaName))
            .entrySet()
            .stream()
            .filter(e -> e.getValue().size() > 1)
            .map(e -> buildProblem(VersionCatalogProblemId.ACCESSOR_NAME_CLASH, spec ->
                spec.inContext(this::standardErrorLocation)
                .withShortDescription(() -> prefix + " " + e.getValue().stream().sorted().collect(Collectors.joining(" and ")) + " are mapped to the same accessor name get" + e.getKey() + suffix + "()")
                .happensBecause("A name clash was detected")
                .addSolution(() -> "Use a different alias for " + e.getValue().stream().sorted().collect(Collectors.joining(" and ")))
                .documented()
            ))
            .collect(Collectors.toList());
        maybeThrowError(ERROR_HEADER, errors);
    }

    private String coordinatesDescriptorFor(DependencyModel dependencyData) {
        return dependencyData.getGroup() + ":" + dependencyData.getName();
    }

    private void writeDependencyAccessor(String alias, String coordinates, @Nullable String context) throws IOException {
        String name = leafNodeForAlias(alias);
        writeLn("    /**");
        writeLn("     * Creates a dependency provider for " + name + " (" + coordinates + ")");
        if (context != null) {
            writeLn("     * This dependency was declared in " + context);
        }
        writeLn("     */");
        writeLn("    public Provider<MinimalExternalModuleDependency> get" + toJavaName(name) + "() { return create(\"" + alias + "\"); }");
        writeLn();
    }

    private String leafNodeForAlias(String alias) {
        List<String> splitted = nameSplitter().splitToList(alias);
        return splitted.get(splitted.size() - 1);
    }

    private void writeSubAccessor(ClassNode classNode, AccessorKind kind) throws IOException {
        String className = classNode.getClassName();
        String getter = classNode.name;
        writeLn("/**");
        writeLn(" * Returns the group of " + kind.getDescription() + " at " + classNode.getPath());
        writeLn(" */");
        writeLn("public " + className + " get" + toJavaName(getter) + "() { return " + kind.accessorVariableNameFor(className) + "; }");
        writeLn();
    }

    private void writeBundle(String alias, List<String> coordinates, @Nullable String context) throws IOException {
        indent(() -> {
            writeLn("/**");
            writeLn(" * Creates a dependency bundle provider for " + alias + " which is an aggregate for the following dependencies:");
            writeLn(" * <ul>");
            for (String coordinate : coordinates) {
                writeLn(" *    <li>" + coordinate + "</li>");
            }
            writeLn(" * </ul>");
            if (context != null) {
                writeLn(" * This bundle was declared in " + context);
            }
            writeLn(" */");
            writeLn("public Provider<ExternalModuleDependencyBundle> get" + toJavaName(leafNodeForAlias(alias)) + "() { return createBundle(\"" + alias + "\"); }");
        });
        writeLn();
    }

    private static ClassNode rootNode(AccessorKind kind) {
        return new ClassNode(kind, null, null);
    }

    private static ClassNode rootNode(AccessorKind kind, String nest) {
        ClassNode root = rootNode(kind);
        ClassNode wrappingNode = root.child(nest);
        wrappingNode.wrapping = true;
        return wrappingNode;
    }

    private static ClassNode toClassNode(List<String> aliases, ClassNode root) {
        for (String alias : aliases) {
            ClassNode current = root;
            // foo -> foo is the alias
            // foo.bar.baz --> baz is the alias
            List<String> dotted = nameSplitter().splitToList(alias);
            int last = dotted.size() - 1;
            for (int i = 0; i < last; i++) {
                current = current.child(dotted.get(i));
            }
            current.addAlias(alias);
        }
        return root;
    }

    private static class ClassNode {
        private final ClassNode parent;
        private final AccessorKind kind;
        private final String name;
        private final Map<String, ClassNode> children = Maps.newLinkedHashMap();
        private final Set<String> aliases = Sets.newLinkedHashSet();
        public boolean wrapping;

        private ClassNode(AccessorKind kind, @Nullable ClassNode parent, @Nullable String name) {
            this.kind = kind;
            this.parent = parent;
            this.name = name;
        }

        private String getSimpleName() {
            if (parent == null || wrapping) {
                return "";
            }
            return parent.getSimpleName() + StringUtils.capitalize(name);
        }

        private String getClassName() {
            return getSimpleName() + kind.getClassNameSuffix();
        }

        ClassNode child(String name) {
            return children.computeIfAbsent(name, n -> new ClassNode(kind, this, n));
        }

        void addAlias(String alias) {
            aliases.add(alias);
        }

        public Collection<ClassNode> getChildren() {
            return children.values();
        }

        public Set<String> getAliases() {
            return aliases;
        }

        String getPath() {
            if (parent == null) {
                return "";
            }
            String parentPath = parent.getPath();
            return parentPath.isEmpty() ? name : parentPath + "." + name;
        }

        private static String shortAlias(String fullAlias) {
            List<String> dotted = nameSplitter().splitToList(fullAlias);
            return dotted.get(dotted.size() - 1);
        }

        void validate() {
            Set<String> intersection = Sets.intersection(aliases.stream().map(ClassNode::shortAlias).collect(Collectors.toSet()), children.keySet());
            if (!intersection.isEmpty()) {
                String path = getPath();
                if (path.isEmpty()) {
                    path = "top level accessors";
                } else {
                    path = "accessors for " + path;
                }
                throw new InvalidUserDataException("Cannot generate " + path + " because it contains both aliases and groups of the same name: " + intersection);
            }
        }

        @Override
        public String toString() {
            return "ClassNode{" +
                "name='" + name + '\'' +
                ", aliases=" + aliases +
                '}';
        }
    }

    private enum AccessorKind {
        library("libraries", "owner"),
        version("versions", "providers, config"),
        bundle("bundles", "providers, config");

        private final String description;
        private final String constructorParams;
        private final String variablePrefix;

        AccessorKind(String description, String constructorParams) {
            this.description = description;
            this.constructorParams = constructorParams;
            this.variablePrefix = name().charAt(0) + "acc";
        }

        public String getDescription() {
            return description;
        }

        public String getClassNameSuffix() {
            return StringUtils.capitalize(name()) + "Accessors";
        }

        public String getConstructorParams() {
            return constructorParams;
        }

        public String accessorVariableNameFor(String className) {
            return variablePrefix + "For" + className;
        }

    }

}
