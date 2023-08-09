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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.NonNullApi;
import org.gradle.api.artifacts.ExternalModuleDependencyBundle;
import org.gradle.api.artifacts.MinimalExternalModuleDependency;
import org.gradle.api.artifacts.MutableVersionConstraint;
import org.gradle.api.internal.artifacts.dsl.CapabilityNotationParser;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.catalog.problems.VersionCatalogProblemId;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.problems.Problems;
import org.gradle.api.problems.interfaces.Problem;
import org.gradle.api.problems.interfaces.ProblemBuilder;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.plugin.use.PluginDependency;
import org.gradle.util.internal.TextUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static org.gradle.api.internal.catalog.problems.DefaultCatalogProblemBuilder.VERSION_CATALOG_PROBLEMS;
import static org.gradle.api.internal.catalog.problems.DefaultCatalogProblemBuilder.getProblemInVersionCatalog;
import static org.gradle.api.internal.catalog.problems.DefaultCatalogProblemBuilder.maybeThrowError;
import static org.gradle.api.internal.catalog.problems.DefaultCatalogProblemBuilder.throwErrorWithNewProblemsApi;
import static org.gradle.api.internal.catalog.problems.VersionCatalogProblemId.ACCESSOR_NAME_CLASH;
import static org.gradle.api.internal.catalog.problems.VersionCatalogProblemId.TOO_MANY_ENTRIES;
import static org.gradle.api.problems.interfaces.ProblemGroup.VERSION_CATALOG_ID;
import static org.gradle.api.problems.interfaces.Severity.ERROR;
import static org.gradle.internal.deprecation.Documentation.userManual;
import static org.gradle.problems.internal.RenderingUtils.oxfordJoin;

public class LibrariesSourceGenerator extends AbstractSourceGenerator {

    private static final int MAX_ENTRIES = 30000;
    public static final String ERROR_HEADER = "Cannot generate dependency accessors";
    private final DefaultVersionCatalog config;
    private final Problems problemService;

    private final Map<String, Integer> classNameCounter = new HashMap<>();
    private final Map<ClassNode, String> classNameCache = new HashMap<>();

    public LibrariesSourceGenerator(
        Writer writer,
        DefaultVersionCatalog config,
        Problems problemService
    ) {
        super(writer);
        this.config = config;
        this.problemService = problemService;
    }

    public static void generateSource(
        Writer writer,
        DefaultVersionCatalog config,
        String packageName,
        String className,
        Problems problemService
    ) {
        LibrariesSourceGenerator generator = new LibrariesSourceGenerator(writer, config, problemService);
        try {
            generator.generateProjectExtensionFactoryClass(packageName, className);
            generator.classNameCounter.clear();
            generator.classNameCache.clear();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void generatePluginsBlockSource(
        Writer writer,
        DefaultVersionCatalog config,
        String packageName,
        String className,
        Problems problemService
    ) {
        LibrariesSourceGenerator generator = new LibrariesSourceGenerator(writer, config, problemService);
        try {
            generator.generatePluginsBlockFactoryClass(packageName, className);
            generator.classNameCounter.clear();
            generator.classNameCache.clear();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void generateProjectExtensionFactoryClass(String packageName, String className) throws IOException {
        generateFactoryClass(packageName, entryPoints ->
            writeEntryPoints(className, entryPoints, false)
        );
    }

    private void generatePluginsBlockFactoryClass(String packageName, String className) throws IOException {
        generateFactoryClass(packageName, entryPoints ->
            writeEntryPoints(className, entryPoints, true)
        );
    }

    private static class EntryPoints {

        private final ClassNode librariesEntryPoint;
        private final ClassNode versionsEntryPoint;
        private final ClassNode bundlesEntryPoint;
        private final ClassNode pluginsEntryPoint;

        private EntryPoints(
            ClassNode librariesEntryPoint,
            ClassNode versionsEntryPoint,
            ClassNode bundlesEntryPoint,
            ClassNode pluginsEntryPoint
        ) {
            this.librariesEntryPoint = librariesEntryPoint;
            this.versionsEntryPoint = versionsEntryPoint;
            this.bundlesEntryPoint = bundlesEntryPoint;
            this.pluginsEntryPoint = pluginsEntryPoint;
        }
    }

    private interface ThrowingConsumer<T> {
        void accept(T t) throws IOException;
    }

    private void generateFactoryClass(String packageName, ThrowingConsumer<EntryPoints> entryPointsConsumer) throws IOException {
        writeLn("package " + packageName + ";");
        writeLn();
        addImports();
        writeLn();
        String description = TextUtil.normaliseLineSeparators(config.getDescription());
        writeLn("/**");
        for (String descLine : Splitter.on('\n').split(description)) {
            writeLn(" * " + descLine);
        }
        List<String> libraries = config.getLibraryAliases();
        List<String> bundles = config.getBundleAliases();
        List<String> versions = config.getVersionAliases();
        List<String> plugins = config.getPluginAliases();
        performValidation(libraries, bundles, versions, plugins);
        entryPointsConsumer.accept(new EntryPoints(
            toClassNode(libraries, rootNode(AccessorKind.library)),
            toClassNode(versions, rootNode(AccessorKind.version, "versions")).parent,
            toClassNode(bundles, rootNode(AccessorKind.bundle, "bundles")).parent,
            toClassNode(plugins, rootNode(AccessorKind.plugin, "plugins")).parent
        ));
    }

    private void writeEntryPoints(String className, EntryPoints entryPoints, boolean deprecated) throws IOException {
        writeLn(" */");
        writeLn("@NonNullApi");
        writeLn("public class " + className + " extends AbstractExternalDependencyFactory {");
        writeLn();
        indent(() -> {
            writeLn("private final AbstractExternalDependencyFactory owner = this;");
            writeSubAccessorFieldsOf(entryPoints.librariesEntryPoint, AccessorKind.library);
            writeSubAccessorFieldsOf(entryPoints.versionsEntryPoint, AccessorKind.version);
            writeSubAccessorFieldsOf(entryPoints.bundlesEntryPoint, AccessorKind.bundle);
            writeSubAccessorFieldsOf(entryPoints.pluginsEntryPoint, AccessorKind.plugin);
            writeLn();
            writeLn("@Inject");
            writeLn("public " + className + "(DefaultVersionCatalog config, ProviderFactory providers, ObjectFactory objects, ImmutableAttributesFactory attributesFactory, CapabilityNotationParser capabilityNotationParser) {");
            writeLn("    super(config, providers, objects, attributesFactory, capabilityNotationParser);");
            writeLn("}");
            writeLn();
            writeLibraryAccessors(entryPoints.librariesEntryPoint, deprecated);
            writeVersionAccessors(entryPoints.versionsEntryPoint);
            writeBundleAccessors(entryPoints.bundlesEntryPoint, deprecated);
            writePluginAccessors(entryPoints.pluginsEntryPoint);
            writeLibrarySubClasses(entryPoints.librariesEntryPoint, deprecated);
            writeVersionSubClasses(entryPoints.versionsEntryPoint);
            writeBundleSubClasses(entryPoints.bundlesEntryPoint, deprecated);
            writePluginSubClasses(entryPoints.pluginsEntryPoint);
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
        addImport(ObjectFactory.class);
        addImport(ProviderFactory.class);
        addImport(AbstractExternalDependencyFactory.class);
        addImport(DefaultVersionCatalog.class);
        addImport(Map.class);
        addImport(ImmutableAttributesFactory.class);
        addImport(CapabilityNotationParser.class);
        addImport(Inject.class);
    }

    private void writeLibrarySubClasses(ClassNode classNode, boolean deprecated) throws IOException {
        for (ClassNode child : classNode.getChildren()) {
            writeLibraryAccessorClass(child, deprecated);
            writeLibrarySubClasses(child, deprecated);
        }
    }

    private void writeVersionSubClasses(ClassNode classNode) throws IOException {
        for (ClassNode child : classNode.getChildren()) {
            writeVersionAccessorClass(child);
            writeVersionSubClasses(child);
        }
    }

    private void writeBundleSubClasses(ClassNode classNode, boolean deprecated) throws IOException {
        for (ClassNode child : classNode.getChildren()) {
            writeBundleAccessorClass(child, deprecated);
            writeBundleSubClasses(child, deprecated);
        }
    }

    private void writePluginSubClasses(ClassNode classNode) throws IOException {
        for (ClassNode child : classNode.getChildren()) {
            writePluginAccessorClass(child);
            writePluginSubClasses(child);
        }
    }

    private void writeBundleAccessorClass(ClassNode classNode, boolean deprecated) throws IOException {
        if (deprecated) {
            writeLn("/**");
            writeDeprecationJavadocTag(true);
            writeLn(" */");
            writeDeprecationAnnotation(true);
        }
        boolean isProvider = classNode.isAlsoProvider();
        String interfaces = isProvider ? " implements BundleNotationSupplier" : "";
        String bundleClassName = getClassName(classNode);
        List<String> aliases = classNode.aliases
            .stream()
            .sorted()
            .collect(toList());
        writeLn("public static class " + bundleClassName + " extends BundleFactory " + interfaces + "{");
        indent(() -> {
            writeSubAccessorFieldsOf(classNode, AccessorKind.bundle);
            writeLn();
            writeLn("public " + bundleClassName + "(ObjectFactory objects, ProviderFactory providers, DefaultVersionCatalog config, ImmutableAttributesFactory attributesFactory, CapabilityNotationParser capabilityNotationParser) { super(objects, providers, config, attributesFactory, capabilityNotationParser); }");
            writeLn();
            if (isProvider) {
                String path = classNode.getFullAlias();
                BundleModel bundle = config.getBundle(path);
                List<String> coordinates = bundle.getComponents().stream()
                    .map(config::getDependencyData)
                    .map(LibrariesSourceGenerator::coordinatesDescriptorFor)
                    .collect(toList());
                writeBundle(path, coordinates, bundle.getContext(), true, deprecated);
            }
            for (String alias : aliases) {
                String childName = leafNodeForAlias(alias);
                if (!classNode.hasChild(childName)) {
                    BundleModel bundle = config.getBundle(alias);
                    List<String> coordinates = bundle.getComponents().stream()
                        .map(config::getDependencyData)
                        .map(LibrariesSourceGenerator::coordinatesDescriptorFor)
                        .collect(toList());
                    writeBundle(alias, coordinates, bundle.getContext(), false, deprecated);
                }
            }
            for (ClassNode child : classNode.getChildren()) {
                writeSubAccessor(child, AccessorKind.bundle, deprecated);
            }
        });
        writeLn("}");
        writeLn();
    }

    private String getClassName(ClassNode classNode) {
        return classNameCache.computeIfAbsent(classNode, this::getClassName0);
    }

    private String getClassName0(ClassNode classNode) {
        String name = classNode.getClassName();
        String loweredName = name.toLowerCase();
        if (!classNameCounter.containsKey(loweredName)) {
            classNameCounter.put(loweredName, 0);
            return name;
        } else {
            int count = classNameCounter.get(loweredName) + 1;
            classNameCounter.put(loweredName, count);
            return name + "$" + count;
        }
    }

    private void writePluginAccessorClass(ClassNode classNode) throws IOException {
        boolean isProvider = classNode.isAlsoProvider();
        String interfaces = isProvider ? " implements PluginNotationSupplier" : "";
        String pluginClassName = getClassName(classNode);
        List<String> aliases = classNode.aliases
            .stream()
            .sorted()
            .collect(toList());
        writeLn("public static class " + pluginClassName + " extends PluginFactory " + interfaces + "{");
        indent(() -> {
            writeSubAccessorFieldsOf(classNode, AccessorKind.plugin);
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
                writeSubAccessor(child, AccessorKind.plugin);
            }
        });
        writeLn("}");
        writeLn();
    }

    private void writeLibraryAccessors(ClassNode classNode, boolean deprecated) throws IOException {
        Set<String> dependencies = classNode.aliases;
        for (String alias : dependencies) {
            String childName = leafNodeForAlias(alias);
            if (!classNode.hasChild(childName)) {
                DependencyModel model = config.getDependencyData(alias);
                String coordinates = coordinatesDescriptorFor(model);
                writeDependencyAccessor(alias, coordinates, model.getContext(), false, deprecated);
            }
        }
        for (ClassNode child : classNode.getChildren()) {
            writeSubAccessor(child, AccessorKind.library, deprecated);
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

    private void writeBundleAccessors(ClassNode classNode, boolean deprecated) throws IOException {
        Set<String> versionsAliases = classNode.aliases;
        for (String alias : versionsAliases) {
            String childName = leafNodeForAlias(alias);
            if (!classNode.hasChild(childName)) {
                BundleModel model = config.getBundle(alias);
                List<String> coordinates = model.getComponents().stream()
                    .map(config::getDependencyData)
                    .map(LibrariesSourceGenerator::coordinatesDescriptorFor)
                    .collect(toList());
                writeBundle(alias, coordinates, model.getContext(), false, deprecated);
            }
        }
        for (ClassNode child : classNode.getChildren()) {
            writeSubAccessor(child, AccessorKind.bundle, deprecated);
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
        String className = getClassName(classNode);
        writeLn("private final " + className + " " + kind.accessorVariableNameFor(className) + " = new " + className + "(" + kind.getConstructorParams() + ");");
    }

    private void writeSubAccessorFieldsOf(ClassNode classNode, AccessorKind kind) throws IOException {
        for (ClassNode child : classNode.getChildren()) {
            writeSubAccessorFieldFor(child, kind);
        }
    }

    private void writeLibraryAccessorClass(ClassNode classNode, boolean deprecated) throws IOException {
        if (deprecated) {
            writeLn("/**");
            writeDeprecationJavadocTag(true);
            writeLn(" */");
            writeDeprecationAnnotation(true);
        }
        boolean isProvider = classNode.isAlsoProvider();
        String interfaces = isProvider ? " implements DependencyNotationSupplier" : "";
        writeLn("public static class " + getClassName(classNode) + " extends SubDependencyFactory" + interfaces + " {");
        indent(() -> {
            writeSubAccessorFieldsOf(classNode, AccessorKind.library);
            writeLn();
            writeLn("public " + getClassName(classNode) + "(AbstractExternalDependencyFactory owner) { super(owner); }");
            writeLn();
            if (isProvider) {
                String path = classNode.getFullAlias();
                DependencyModel model = config.getDependencyData(path);
                writeDependencyAccessor(path, coordinatesDescriptorFor(model), model.getContext(), true, deprecated);
            }
            for (String alias : classNode.aliases) {
                String childName = leafNodeForAlias(alias);
                if (!classNode.hasChild(childName)) {
                    DependencyModel model = config.getDependencyData(alias);
                    String coordinates = coordinatesDescriptorFor(model);
                    writeDependencyAccessor(alias, coordinates, model.getContext(), false, deprecated);
                }
            }
            for (ClassNode child : classNode.getChildren()) {
                writeSubAccessor(child, AccessorKind.library, deprecated);
            }
        });
        writeLn("}");
        writeLn();
    }

    private void writeVersionAccessorClass(ClassNode classNode) throws IOException {
        boolean isProvider = classNode.isAlsoProvider();
        String interfaces = isProvider ? " implements VersionNotationSupplier" : "";
        String versionsClassName = getClassName(classNode);
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
            writeLn(" * This version was declared in " + sanitizeUnicodeEscapes(context));
        }
        writeLn(" */");
        String methodName = asProvider ? "asProvider" : "get" + toJavaName(leafNodeForAlias(versionAlias));
        writeLn("public Provider<String> " + methodName + "() { return getVersion(\"" + versionAlias + "\"); }");
        writeLn();
    }

    private String standardErrorLocation() {
        return "version catalog " + config.getName();
    }

    private void performValidation(List<String> libraries, List<String> bundles, List<String> versions, List<String> plugins) {
        assertUnique(libraries, "library aliases", "");
        assertUnique(bundles, "dependency bundles", "Bundle");
        assertUnique(versions, "dependency versions", "Version");
        assertUnique(plugins, "plugins", "Plugin");
        int size = libraries.size() + bundles.size() + versions.size() + plugins.size();
        if (size > MAX_ENTRIES) {
            throw throwVersionCatalogProblemException(createVersionCatalogError(gerProblemPrefix() + "version catalog model contains too many entries (" + size + ").", TOO_MANY_ENTRIES)
                .description("The maximum number of aliases in a catalog is " + MAX_ENTRIES)
                .solution("Reduce the number of aliases defined in this catalog")
                .solution("Split the catalog into multiple catalogs"));
        }
    }

    private RuntimeException throwVersionCatalogProblemException(ProblemBuilder problem) {
        throw throwErrorWithNewProblemsApi(ERROR_HEADER, ImmutableList.of(problem.build()), problemService);
    }

    private void assertUnique(List<String> names, String prefix, String suffix) {
        List<Problem> errors = names.stream()
            .collect(groupingBy(AbstractSourceGenerator::toJavaName))
            .entrySet()
            .stream()
            .filter(e -> e.getValue().size() > 1)
            .map(e -> {
                String errorValues = e.getValue().stream().sorted().collect(oxfordJoin("and"));
                return createVersionCatalogError(gerProblemPrefix() + prefix + " " + errorValues + " are mapped to the same accessor name get" + e.getKey() + suffix + "().", ACCESSOR_NAME_CLASH)
                    .description("A name clash was detected")
                    .solution("Use a different alias for " + errorValues)
                    .build();
            })
            .collect(toList());
        maybeThrowError(ERROR_HEADER, errors, problemService);
    }

    @Nonnull
    private String gerProblemPrefix() {
        return getProblemInVersionCatalog(config.getName()) + ", ";
    }

    private static String coordinatesDescriptorFor(DependencyModel dependencyData) {
        return dependencyData.getGroup() + ":" + dependencyData.getName();
    }

    private void writeDependencyAccessor(String alias, String coordinates, @Nullable String context, boolean asProvider, boolean deprecated) throws IOException {
        String name = leafNodeForAlias(alias);
        writeLn("    /**");
        writeLn("     * Creates a dependency provider for " + name + " (" + coordinates + ")");
        if (context != null) {
            writeLn("     * This dependency was declared in " + sanitizeUnicodeEscapes(context));
        }
        writeDeprecationJavadocTag(deprecated);
        writeLn("     */");
        writeDeprecationAnnotation(deprecated);
        String methodName = asProvider ? "asProvider" : "get" + toJavaName(name);
        writeLn("    public Provider<MinimalExternalModuleDependency> " + methodName + "() {");
        writeDeprecationLog(deprecated);
        writeLn("        return create(\"" + alias + "\");");
        writeLn("}");
        writeLn();
    }

    private static String leafNodeForAlias(String alias) {
        List<String> split = nameSplitter().splitToList(alias);
        return split.get(split.size() - 1);
    }

    private void writeSubAccessor(ClassNode classNode, AccessorKind kind) throws IOException {
        writeSubAccessor(classNode, kind, false);
    }

    private void writeSubAccessor(ClassNode classNode, AccessorKind kind, boolean deprecated) throws IOException {
        String className = getClassName(classNode);
        String getter = classNode.name;
        writeLn("/**");
        writeLn(" * Returns the group of " + kind.getDescription() + " at " + classNode.getPath());
        writeDeprecationJavadocTag(deprecated);
        writeLn(" */");
        writeDeprecationAnnotation(deprecated);
        writeLn("public " + className + " get" + toJavaName(getter) + "() {");
        writeDeprecationLog(deprecated);
        writeLn("    return " + kind.accessorVariableNameFor(className) + ";");
        writeLn("}");
        writeLn();
    }

    private void writeBundle(String alias, List<String> coordinates, @Nullable String context, boolean asProvider, boolean deprecated) throws IOException {
        indent(() -> {
            writeLn("/**");
            writeLn(" * Creates a dependency bundle provider for " + alias + " which is an aggregate for the following dependencies:");
            writeLn(" * <ul>");
            for (String coordinate : coordinates) {
                writeLn(" *    <li>" + coordinate + "</li>");
            }
            writeLn(" * </ul>");
            if (context != null) {
                writeLn(" * This bundle was declared in " + sanitizeUnicodeEscapes(context));
            }
            writeDeprecationJavadocTag(deprecated);
            writeLn(" */");
            writeDeprecationAnnotation(deprecated);
            String methodName = asProvider ? "asProvider" : "get" + toJavaName(leafNodeForAlias(alias));
            writeLn("public Provider<ExternalModuleDependencyBundle> " + methodName + "() {");
            writeDeprecationLog(deprecated);
            writeLn("    return createBundle(\"" + alias + "\");");
            writeLn("}");
        });
        writeLn();
    }

    private void writeDeprecationJavadocTag(boolean deprecated) throws IOException {
        if (deprecated) {
            writeLn(" * @deprecated Will be removed in Gradle 9.0.");
        }
    }

    private void writeDeprecationAnnotation(boolean deprecated) throws IOException {
        if (deprecated) {
            writeLn("@Deprecated");
        }
    }

    private void writeDeprecationLog(boolean deprecated) throws IOException {
        if (deprecated) {
            writeLn("    " +
                DeprecationLogger.class.getName() +
                ".deprecateBehaviour(\"Accessing libraries or bundles from version catalogs in the plugins block.\")" +
                ".withAdvice(\"Only use versions or plugins from catalogs in the plugins block.\")" +
                ".willBeRemovedInGradle9()" +
                ".withUpgradeGuideSection(8, \"kotlin_dsl_deprecated_catalogs_plugins_block\")" +
                ".nagUser();"
            );
        }
    }

    private void writePlugin(String alias, String id, @Nullable String context, boolean asProvider) throws IOException {
        indent(() -> {
            writeLn("/**");
            writeLn(" * Creates a plugin provider for " + alias + " to the plugin id '" + id + "'");
            if (context != null) {
                writeLn(" * This plugin was declared in " + sanitizeUnicodeEscapes(context));
            }
            writeLn(" */");
            String methodName = asProvider ? "asProvider" : "get" + toJavaName(leafNodeForAlias(alias));
            writeLn("public Provider<PluginDependency> " + methodName + "() { return createPlugin(\"" + alias + "\"); }");
        });
        writeLn();
    }

    /**
     * Java compiler would fail to compile sources that have illegal unicode escape characters, including in the comments.
     * Such characters could be accidentally introduced by a backslash followed by {@code 'u'},
     * e.g. in Windows path {@code '..\\user\dir'}.
     */
    private static String sanitizeUnicodeEscapes(String s) {
        // If a backslash precedes 'u', then we replace the backslash with its unicode notation '\\u005c'
        return s.replace("\\u", "\\u005cu");
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
        bundle("bundles", "objects, providers, config, attributesFactory, capabilityNotationParser"),
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
    @Nonnull
    public ProblemBuilder createVersionCatalogError(String message, VersionCatalogProblemId catalogProblemId) {
        return problemService.createProblemBuilder()//VERSION_CATALOG, message, ERROR, catalogProblemId.name())
            .documentedAt(userManual(VERSION_CATALOG_PROBLEMS, catalogProblemId.name().toLowerCase()))
            .noLocation()
            .severity(ERROR)
            .message(message)
            .type(catalogProblemId.name().toLowerCase())
            .group(VERSION_CATALOG_ID);
    }

}
