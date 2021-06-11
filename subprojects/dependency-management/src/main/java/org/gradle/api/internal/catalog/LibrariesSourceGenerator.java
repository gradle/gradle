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
import org.gradle.api.NonNullApi;
import org.gradle.api.artifacts.ExternalModuleDependencyBundle;
import org.gradle.api.artifacts.MinimalExternalModuleDependency;
import org.gradle.api.artifacts.MutableVersionConstraint;
import org.gradle.api.internal.catalog.problems.VersionCatalogProblem;
import org.gradle.api.internal.catalog.problems.VersionCatalogProblemId;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.plugin.use.PluginDependency;
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
        List<String> plugins = config.getPluginAliases();
        performValidation(libraries, bundles, versions, plugins);
        ClassNode librariesEntryPoint = toClassNode(libraries, rootNode(AccessorKind.library));
        ClassNode versionsEntryPoint = toClassNode(versions, rootNode(AccessorKind.version, "versions")).parent;
        ClassNode bundlesEntryPoint = toClassNode(bundles, rootNode(AccessorKind.bundle, "bundles")).parent;
        ClassNode pluginsEntryPoint = toClassNode(plugins, rootNode(AccessorKind.plugin, "plugins")).parent;
        writeLibraryEntryPoint(className, librariesEntryPoint, versionsEntryPoint, bundlesEntryPoint, pluginsEntryPoint);
    }

    private void writeLibraryEntryPoint(String className, ClassNode librariesEntryPoint, ClassNode versionsEntryPoint, ClassNode bundlesEntryPoint, ClassNode pluginsEntryPoint) throws IOException {
        writeLn("*/");
        writeLn("@NonNullApi");
        writeLn("public class " + className + " extends AbstractExternalDependencyFactory {");
        writeLn();
        indent(() -> {
            writeLn("private final AbstractExternalDependencyFactory owner = this;");
            writeSubAccessorFieldsOf(librariesEntryPoint, AccessorKind.library);
            writeSubAccessorFieldsOf(versionsEntryPoint, AccessorKind.version);
            writeSubAccessorFieldsOf(bundlesEntryPoint, AccessorKind.bundle);
            writeSubAccessorFieldsOf(pluginsEntryPoint, AccessorKind.plugin);
            writeLn();
            writeLn("@Inject");
            writeLn("public " + className + "(DefaultVersionCatalog config, ProviderFactory providers) {");
            writeLn("    super(config, providers);");
            writeLn("}");
            writeLn();
            writeLibraryAccessors(librariesEntryPoint);
            writeVersionAccessors(versionsEntryPoint);
            writeBundleAccessors(bundlesEntryPoint);
            writePluginAccessors(pluginsEntryPoint);
            writeLibrarySubClasses(librariesEntryPoint);
            writeVersionSubClasses(versionsEntryPoint);
            writeBundleSubClasses(bundlesEntryPoint);
            writePluginSubClasses(pluginsEntryPoint);
        });
        writeLn("}");
    }

    private void addImports() throws IOException {
        addImport(NonNullApi.class);
        addImport(MinimalExternalModuleDependency.class);
        addImport(PluginDependency.class);
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

    private void writePluginSubClasses(ClassNode classNode) throws IOException {
        for (ClassNode child : classNode.getChildren()) {
            writePluginAccessorClass(child);
            writePluginSubClasses(child);
        }
    }

    private void writeBundleAccessorClass(ClassNode classNode) throws IOException {
        boolean isProvider = classNode.isAlsoProvider();
        String interfaces = isProvider ? " implements BundleNotationSupplier":"";
        String bundleClassName = classNode.getClassName();
        List<String> aliases = classNode.aliases
            .stream()
            .sorted()
            .collect(Collectors.toList());
        writeLn("public static class " + bundleClassName + " extends BundleFactory " + interfaces + "{");
        indent(() -> {
            writeSubAccessorFieldsOf(classNode, AccessorKind.bundle);
            writeLn();
            writeLn("public " + bundleClassName + "(ProviderFactory providers, DefaultVersionCatalog config) { super(providers, config); }");
            writeLn();
            if (isProvider) {
                String path = classNode.getFullAlias();
                BundleModel bundle = config.getBundle(path);
                List<String> coordinates = bundle.getComponents().stream()
                    .map(config::getDependencyData)
                    .map(this::coordinatesDescriptorFor)
                    .collect(Collectors.toList());
                writeBundle(path, coordinates, bundle.getContext(), true);
            }
            for (String alias : aliases) {
                String childName = leafNodeForAlias(alias);
                if (!classNode.hasChild(childName)) {
                    BundleModel bundle = config.getBundle(alias);
                    List<String> coordinates = bundle.getComponents().stream()
                        .map(config::getDependencyData)
                        .map(this::coordinatesDescriptorFor)
                        .collect(Collectors.toList());
                    writeBundle(alias, coordinates, bundle.getContext(), false);
                }
            }
            for (ClassNode child : classNode.getChildren()) {
                writeSubAccessor(child, AccessorKind.bundle);
            }
        });
        writeLn("}");
        writeLn();
    }

    private void writePluginAccessorClass(ClassNode classNode) throws IOException {
        boolean isProvider = classNode.isAlsoProvider();
        String interfaces = isProvider ? " implements PluginNotationSupplier":"";
        String pluginClassName = classNode.getClassName();
        List<String> aliases = classNode.aliases
            .stream()
            .sorted()
            .collect(Collectors.toList());
        writeLn("public static class " + pluginClassName + " extends PluginFactory " + interfaces + "{");
        indent(() -> {
            writeSubAccessorFieldsOf(classNode, AccessorKind.bundle);
            writeLn();
            writeLn("public " + pluginClassName + "(ProviderFactory providers, DefaultVersionCatalog config) { super(providers, config); }");
            writeLn();
            if (isProvider) {
                String path = classNode.getFullAlias();
                PluginModel plugin = config.getPlugin(path);
                writePlugin(path, plugin.getId(), plugin.getContext(), true);
            }
            for (String alias : aliases) {
                String childName = leafNodeForAlias(alias);
                if (!classNode.hasChild(childName)) {
                    PluginModel plugin = config.getPlugin(alias);
                    writePlugin(alias, plugin.getId(), plugin.getContext(), false);
                }
            }
            for (ClassNode child : classNode.getChildren()) {
                writeSubAccessor(child, AccessorKind.bundle);
            }
        });
        writeLn("}");
        writeLn();
    }

    private void writeLibraryAccessors(ClassNode classNode) throws IOException {
        Set<String> dependencies = classNode.aliases;
        for (String alias : dependencies) {
            String childName = leafNodeForAlias(alias);
            if (!classNode.hasChild(childName)) {
                DependencyModel model = config.getDependencyData(alias);
                String coordinates = coordinatesDescriptorFor(model);
                writeDependencyAccessor(alias, coordinates, model.getContext(), false);
            }
        }
        for (ClassNode child : classNode.getChildren()) {
            writeSubAccessor(child, AccessorKind.library);
        }
    }

    private void writeVersionAccessors(ClassNode classNode) throws IOException {
        Set<String> versionsAliases = classNode.aliases;
        for (String alias : versionsAliases) {
            String childName = leafNodeForAlias(alias);
            if (!classNode.hasChild(childName)) {
                VersionModel model = config.getVersion(alias);
                writeSingleVersionAccessor(alias, model.getContext(), model.getVersion().getDisplayName(), false);
            }
        }
        for (ClassNode child : classNode.getChildren()) {
            writeSubAccessor(child, AccessorKind.version);
        }
    }

    private void writeBundleAccessors(ClassNode classNode) throws IOException {
        Set<String> versionsAliases = classNode.aliases;
        for (String alias : versionsAliases) {
            String childName = leafNodeForAlias(alias);
            if (!classNode.hasChild(childName)) {
                BundleModel model = config.getBundle(alias);
                List<String> coordinates = model.getComponents().stream()
                    .map(config::getDependencyData)
                    .map(this::coordinatesDescriptorFor)
                    .collect(Collectors.toList());
                writeBundle(alias, coordinates, model.getContext(), false);
            }
        }
        for (ClassNode child : classNode.getChildren()) {
            writeSubAccessor(child, AccessorKind.bundle);
        }
    }

    private void writePluginAccessors(ClassNode classNode) throws IOException {
        Set<String> versionsAliases = classNode.aliases;
        for (String alias : versionsAliases) {
            String childName = leafNodeForAlias(alias);
            if (!classNode.hasChild(childName)) {
                PluginModel model = config.getPlugin(alias);
                writePlugin(alias, model.getId(), model.getContext(), false);
            }
        }
        for (ClassNode child : classNode.getChildren()) {
            writeSubAccessor(child, AccessorKind.plugin);
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
        boolean isProvider = classNode.isAlsoProvider();
        String interfaces = isProvider ? " implements DependencyNotationSupplier":"";
        writeLn("public static class " + classNode.getClassName() + " extends SubDependencyFactory" + interfaces + " {");
        indent(() -> {
            writeSubAccessorFieldsOf(classNode, AccessorKind.library);
            writeLn();
            writeLn("public " + classNode.getClassName() + "(AbstractExternalDependencyFactory owner) { super(owner); }");
            writeLn();
            if (isProvider) {
                String path = classNode.getFullAlias();
                DependencyModel model = config.getDependencyData(path);
                writeDependencyAccessor(path, coordinatesDescriptorFor(model), model.getContext(), true);
            }
            for (String alias : classNode.aliases) {
                String childName = leafNodeForAlias(alias);
                if (!classNode.hasChild(childName)) {
                    DependencyModel model = config.getDependencyData(alias);
                    String coordinates = coordinatesDescriptorFor(model);
                    writeDependencyAccessor(alias, coordinates, model.getContext(), false);
                }
            }
            for (ClassNode child : classNode.getChildren()) {
                writeSubAccessor(child, AccessorKind.library);
            }
        });
        writeLn("}");
        writeLn();
    }

    private void writeVersionAccessorClass(ClassNode classNode) throws IOException {
        boolean isProvider = classNode.isAlsoProvider();
        String interfaces = isProvider ? " implements VersionNotationSupplier":"";
        String versionsClassName = classNode.getClassName();
        Set<String> versionAliases = classNode.getAliases();
        writeLn("public static class " + versionsClassName + " extends VersionFactory " + interfaces + " {");
        writeLn();
        indent(() -> {
            writeSubAccessorFieldsOf(classNode, AccessorKind.version);
            writeLn("public " + versionsClassName + "(ProviderFactory providers, DefaultVersionCatalog config) { super(providers, config); }");
            writeLn();
            if (isProvider) {
                String path = classNode.getFullAlias();
                VersionModel vm = config.getVersion(path);
                String context = vm.getContext();
                writeSingleVersionAccessor(path, context, vm.getVersion().getDisplayName(), true);
            }
            for (String alias : versionAliases) {
                String childName = leafNodeForAlias(alias);
                if (!classNode.hasChild(childName)) {
                    VersionModel vm = config.getVersion(alias);
                    String context = vm.getContext();
                    indent(() -> writeSingleVersionAccessor(alias, context, vm.getVersion().getDisplayName(), false));
                }
            }
            for (ClassNode child : classNode.getChildren()) {
                writeSubAccessor(child, AccessorKind.version);
            }
        });
        writeLn("}");
        writeLn();
    }

    private void writeSingleVersionAccessor(String versionAlias, @Nullable String context, String version, boolean asProvider) throws IOException {
        writeLn("/**");
        writeLn(" * Returns the version associated to this alias: " + versionAlias + " (" + version + ")");
        writeLn(" * If the version is a rich version and that its not expressible as a");
        writeLn(" * single version string, then an empty string is returned.");
        if (context != null) {
            writeLn(" * This version was declared in " + context);
        }
        writeLn(" */");
        String methodName = asProvider ? "asProvider" : "get" + toJavaName(leafNodeForAlias(versionAlias));
        writeLn("public Provider<String> " +  methodName + "() { return getVersion(\"" + versionAlias + "\"); }");
        writeLn();
    }

    private String standardErrorLocation() {
        return "Version catalog " + config.getName();
    }

    private void performValidation(List<String> dependencies, List<String> bundles, List<String> versions, List<String> plugins) {
        assertUnique(dependencies, "dependency aliases", "");
        assertUnique(bundles, "dependency bundles", "Bundle");
        assertUnique(versions, "dependency versions", "Version");
        assertUnique(plugins, "plugins", "Plugin");
        int size = dependencies.size() + bundles.size() + versions.size() + plugins.size();
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

    private void writeDependencyAccessor(String alias, String coordinates, @Nullable String context, boolean asProvider) throws IOException {
        String name = leafNodeForAlias(alias);
        writeLn("    /**");
        writeLn("     * Creates a dependency provider for " + name + " (" + coordinates + ")");
        if (context != null) {
            writeLn("     * This dependency was declared in " + context);
        }
        writeLn("     */");
        String methodName = asProvider ? "asProvider": "get" + toJavaName(name);
        writeLn("    public Provider<MinimalExternalModuleDependency> " + methodName + "() { return create(\"" + alias + "\"); }");
        writeLn();
    }

    private static String leafNodeForAlias(String alias) {
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

    private void writeBundle(String alias, List<String> coordinates, @Nullable String context, boolean asProvider) throws IOException {
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
            String methodName = asProvider ? "asProvider" : "get" + toJavaName(leafNodeForAlias(alias));
            writeLn("public Provider<ExternalModuleDependencyBundle> " + methodName + "() { return createBundle(\"" + alias + "\"); }");
        });
        writeLn();
    }

    private void writePlugin(String alias, String id, @Nullable String context, boolean asProvider) throws IOException {
        indent(() -> {
            writeLn("/**");
            writeLn(" * Creates a plugin provider for " + alias + " to the plugin id '" + id + "'");
            if (context != null) {
                writeLn(" * This plugin was declared in " + context);
            }
            writeLn(" */");
            String methodName = asProvider ? "asProvider" : "get" + toJavaName(leafNodeForAlias(alias));
            writeLn("public Provider<PluginDependency> " + methodName + "() { return createPlugin(\"" + alias + "\"); }");
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
        private final Set<String> leafAliases = Sets.newLinkedHashSet();
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
            leafAliases.add(leafNodeForAlias(alias));
        }

        public Collection<ClassNode> getChildren() {
            return children.values();
        }

        public Set<String> getAliases() {
            return aliases;
        }

        public boolean hasChild(String name) {
            return children.containsKey(name);
        }

        String getPath() {
            if (parent == null) {
                return "";
            }
            String parentPath = parent.getPath();
            return parentPath.isEmpty() ? name : parentPath + "." + name;
        }

        String getFullAlias() {
            if (parent == null || wrapping) {
                return "";
            }
            String parentPath = parent.getFullAlias();
            return parentPath.isEmpty() ? name : parentPath + "." + name;
        }

        public boolean isAlsoProvider() {
            return parent != null &&
                parent.leafAliases.contains(name) &&
                parent.children.containsKey(name);
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
        bundle("bundles", "providers, config"),
        plugin("plugins", "providers, config");

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
