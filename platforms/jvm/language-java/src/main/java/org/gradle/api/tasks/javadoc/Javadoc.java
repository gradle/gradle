/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.tasks.javadoc;

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.gradle.api.Action;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.internal.StartParameterInternal;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.internal.tasks.compile.CompilationSourceDirs;
import org.gradle.api.jvm.ModularitySpec;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SourceTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.javadoc.internal.JavadocExecutableUtils;
import org.gradle.api.tasks.javadoc.internal.JavadocSpec;
import org.gradle.api.tasks.javadoc.internal.JavadocToolAdapter;
import org.gradle.external.javadoc.MinimalJavadocOptions;
import org.gradle.external.javadoc.StandardJavadocDocletOptions;
import org.gradle.internal.file.Deleter;
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty;
import org.gradle.internal.jvm.DefaultModularitySpec;
import org.gradle.internal.jvm.JavaModuleDetector;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.jvm.toolchain.JavadocTool;
import org.gradle.jvm.toolchain.internal.JavaExecutableUtils;
import org.gradle.util.internal.ConfigureUtil;
import org.gradle.util.internal.GFileUtils;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

import static org.gradle.util.internal.GUtil.isTrue;

/**
 * <p>Generates HTML API documentation for Java classes.</p>
 * <p>
 * If you create your own Javadoc tasks remember to specify the 'source' property!
 * Without source the Javadoc task will not create any documentation. Example:
 * <pre class='autoTested'>
 * plugins {
 *     id 'java'
 * }
 *
 * task myJavadocs(type: Javadoc) {
 *   source = sourceSets.main.allJava
 * }
 * </pre>
 *
 * <p>
 * An example how to create a task that runs a custom doclet implementation:
 * <pre class='autoTested'>
 * plugins {
 *     id 'java'
 * }
 *
 * configurations {
 *   jaxDoclet
 * }
 *
 * dependencies {
 *   //jaxDoclet "some.interesting:Dependency:1.0"
 * }
 *
 * task generateRestApiDocs(type: Javadoc) {
 *   source = sourceSets.main.allJava
 *   destinationDir = reporting.file("rest-api-docs")
 *   options.docletpath = configurations.jaxDoclet.files.asType(List)
 *   options.doclet = "com.lunatech.doclets.jax.jaxrs.JAXRSDoclet"
 *   options.addStringOption("jaxrscontext", "http://localhost:8080/myapp")
 * }
 * </pre>
 */
@CacheableTask
public abstract class Javadoc extends SourceTask {

    private File destinationDir;

    private boolean failOnError = true;

    private String title;

    private String maxMemory;

    private final StandardJavadocDocletOptions options = new StandardJavadocDocletOptions();

    private FileCollection classpath = getProject().files();
    private final ModularitySpec modularity;

    private String executable;
    private final Property<JavadocTool> javadocTool;

    private final boolean offline;

    public Javadoc() {
        ObjectFactory objectFactory = getObjectFactory();
        this.modularity = objectFactory.newInstance(DefaultModularitySpec.class);
        JavaToolchainService javaToolchainService = getJavaToolchainService();
        Provider<JavadocTool> javadocToolConvention = getProviderFactory()
            .provider(() -> JavadocExecutableUtils.getExecutableOverrideToolchainSpec(this, objectFactory))
            .flatMap(javaToolchainService::javadocToolFor)
            .orElse(javaToolchainService.javadocToolFor(it -> {}));
        this.javadocTool = objectFactory.property(JavadocTool.class).convention(javadocToolConvention);
        this.javadocTool.finalizeValueOnRead();
        this.offline = getStartParameter().isOffline();
    }

    @TaskAction
    protected void generate() {
        if (offline) {
            prepareForOfflineOperation();
        }

        File destinationDir = getDestinationDir();
        try {
            getDeleter().ensureEmptyDirectory(destinationDir);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }

        StandardJavadocDocletOptions options = new StandardJavadocDocletOptions((StandardJavadocDocletOptions) getOptions());

        if (options.getDestinationDirectory() == null) {
            options.destinationDirectory(destinationDir);
        }

        boolean isModule = isModule();
        JavaModuleDetector javaModuleDetector = getJavaModuleDetector();
        options.classpath(new ArrayList<>(javaModuleDetector.inferClasspath(isModule, getClasspath()).getFiles()));
        options.modulePath(new ArrayList<>(javaModuleDetector.inferModulePath(isModule, getClasspath()).getFiles()));
        if (options.getBootClasspath() != null && !options.getBootClasspath().isEmpty()) {
            // Added so JavaDoc has the same behavior as JavaCompile regarding the bootClasspath
            getProjectLayout().files(options.getBootClasspath()).getAsPath();
        }

        if (!isTrue(options.getWindowTitle()) && isTrue(getTitle())) {
            options.windowTitle(getTitle());
        }
        if (!isTrue(options.getDocTitle()) && isTrue(getTitle())) {
            options.setDocTitle(getTitle());
        }

        String maxMemory = getMaxMemory();
        if (maxMemory != null && options.getJFlags().stream().noneMatch(flag -> flag.startsWith("-Xmx"))) {
            options.jFlags("-Xmx" + maxMemory);
        }

        options.setSourceNames(sourceNames());

        JavadocSpec spec = createJavadocSpec(options);
        getJavadocToolAdapter().execute(spec);
    }

    private void validateExecutableMatchesToolchain() {
        File toolchainExecutable = getJavadocTool().get().getExecutablePath().getAsFile();
        String customExecutable = getExecutable();
        JavaExecutableUtils.validateExecutable(
                customExecutable, "Toolchain from `executable` property",
                toolchainExecutable, "toolchain from `javadocTool` property");
    }

    private boolean isModule() {
        List<File> sourcesRoots = CompilationSourceDirs.inferSourceRoots((FileTreeInternal) getSource());
        return JavaModuleDetector.isModuleSource(modularity.getInferModulePath().get(), sourcesRoots);
    }

    private List<String> sourceNames() {
        List<String> sourceNames = new ArrayList<>();
        for (File sourceFile : getSource()) {
            sourceNames.add(sourceFile.getAbsolutePath());
        }
        return sourceNames;
    }

    private JavadocSpec createJavadocSpec(StandardJavadocDocletOptions options) {
        validateExecutableMatchesToolchain();

        JavadocSpec spec = new JavadocSpec();
        spec.setOptions(options);
        spec.setIgnoreFailures(!isFailOnError());
        spec.setWorkingDir(getProjectLayout().getProjectDirectory().getAsFile());
        spec.setOptionsFile(getOptionsFile());

        JavadocToolAdapter javadocToolAdapter = getJavadocToolAdapter();
        spec.setExecutable(javadocToolAdapter.getExecutablePath().toString());

        return spec;
    }

    private JavadocToolAdapter getJavadocToolAdapter() {
        return (JavadocToolAdapter) getJavadocTool().get();
    }

    private void prepareForOfflineOperation() {
        List<String> links = options.getLinks();
        if (links != null && !links.isEmpty()) {
            File fallbackPackageList = setupFallbackPackageList();
            substituteLinksForOfflineOperation(links, fallbackPackageList);
        }
    }

    private File setupFallbackPackageList() {
        File packageFile = getProjectLayout().getBuildDirectory().file("javadoc/offline-fallback/package-list").get().getAsFile();
        GFileUtils.mkdirs(packageFile.getParentFile());
        GFileUtils.touch(packageFile);
        return packageFile;
    }

    private void substituteLinksForOfflineOperation(List<String> links, File fallbackPackageList) {
        links.forEach(link -> {
            options.linksOffline(link, fallbackPackageList.getParentFile().getAbsolutePath());
        });

        links.clear();
    }

    /**
     * {@inheritDoc}
     */
    @PathSensitive(PathSensitivity.RELATIVE)
    @Override
    @ToBeReplacedByLazyProperty
    public FileTree getSource() {
        return super.getSource();
    }

    /**
     * Configures the javadoc executable to be used to generate javadoc documentation.
     *
     * @since 6.7
     */
    @Nested
    public Property<JavadocTool> getJavadocTool() {
        return javadocTool;
    }

    /**
     * <p>Returns the directory to generate the documentation into.</p>
     *
     * @return The directory.
     */
    @Internal
    @Nullable
    @ToBeReplacedByLazyProperty
    public File getDestinationDir() {
        return destinationDir;
    }

    @OutputDirectory
    protected File getOutputDirectory() {
        File destinationDir = getDestinationDir();
        if (destinationDir == null) {
            destinationDir = options.getDestinationDirectory();
        }
        return destinationDir;
    }

    /**
     * <p>Sets the directory to generate the documentation into.</p>
     */
    public void setDestinationDir(File destinationDir) {
        this.destinationDir = destinationDir;
    }

    /**
     * Returns the amount of memory allocated to this task.
     */
    @Internal
    @Nullable
    @ToBeReplacedByLazyProperty
    public String getMaxMemory() {
        return maxMemory;
    }

    /**
     * Sets the amount of memory allocated to this task.
     *
     * @param maxMemory The amount of memory
     */
    public void setMaxMemory(String maxMemory) {
        this.maxMemory = maxMemory;
    }

    /**
     * <p>Returns the title for the generated documentation.</p>
     *
     * @return The title, possibly null.
     */
    @Nullable
    @Optional
    @Input
    @ToBeReplacedByLazyProperty
    public String getTitle() {
        return title;
    }

    /**
     * <p>Sets the title for the generated documentation.</p>
     */
    public void setTitle(@Nullable String title) {
        this.title = title;
    }

    /**
     * Returns whether Javadoc generation is accompanied by verbose output.
     *
     * @see #setVerbose(boolean)
     */
    @Internal
    @ToBeReplacedByLazyProperty
    public boolean isVerbose() {
        return options.isVerbose();
    }

    /**
     * Sets whether Javadoc generation is accompanied by verbose output or not. The verbose output is done via println
     * (by the underlying Ant task). Thus it is not handled by our logging.
     *
     * @param verbose Whether the output should be verbose.
     */
    public void setVerbose(boolean verbose) {
        if (verbose) {
            options.verbose();
        }
    }

    /**
     * Returns the classpath to use to resolve type references in the source code.
     *
     * @return The classpath.
     */
    @Classpath
    @ToBeReplacedByLazyProperty
    public FileCollection getClasspath() {
        return classpath;
    }

    /**
     * Sets the classpath to use to resolve type references in this source code.
     *
     * @param classpath The classpath. Must not be null.
     */
    public void setClasspath(FileCollection classpath) {
        this.classpath = classpath;
    }

    /**
     * Returns the module path handling of this javadoc task.
     *
     * @since 6.4
     */
    @Nested
    public ModularitySpec getModularity() {
        return modularity;
    }

    /**
     * Returns the Javadoc generation options.
     *
     * @return The options. Never returns null.
     */
    @Nested
    public MinimalJavadocOptions getOptions() {
        return options;
    }

    /**
     * Convenience method for configuring Javadoc generation options.
     *
     * @param block The configuration block for Javadoc generation options.
     */
    public void options(@DelegatesTo(MinimalJavadocOptions.class) Closure<?> block) {
        ConfigureUtil.configure(block, getOptions());
    }

    /**
     * Convenience method for configuring Javadoc generation options.
     *
     * @param action The action for Javadoc generation options.
     * @since 3.5
     */
    public void options(Action<? super MinimalJavadocOptions> action) {
        action.execute(getOptions());
    }

    /**
     * Specifies whether this task should fail when errors are encountered during Javadoc generation. When {@code true},
     * this task will fail on Javadoc error. When {@code false}, this task will ignore Javadoc errors.
     */
    @Input
    @ToBeReplacedByLazyProperty
    public boolean isFailOnError() {
        return failOnError;
    }

    public void setFailOnError(boolean failOnError) {
        this.failOnError = failOnError;
    }

    @Internal
    @ToBeReplacedByLazyProperty
    public File getOptionsFile() {
        return new File(getTemporaryDir(), "javadoc.options");
    }

    /**
     * Returns the Javadoc executable to use to generate the Javadoc. When {@code null}, the Javadoc executable for
     * the current JVM is used or from the toolchain if configured.
     *
     * @return The executable. May be null.
     * @see #getJavadocTool()
     */
    @Nullable
    @Optional
    @Input
    @ToBeReplacedByLazyProperty
    public String getExecutable() {
        return executable;
    }

    public void setExecutable(@Nullable String executable) {
        this.executable = executable;
    }

    @Inject
    protected Deleter getDeleter() {
        throw new UnsupportedOperationException("Decorator takes care of injection");
    }

    @Inject
    protected ProjectLayout getProjectLayout() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected ObjectFactory getObjectFactory() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected JavaModuleDetector getJavaModuleDetector() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected JavaToolchainService getJavaToolchainService() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected ProviderFactory getProviderFactory() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected StartParameterInternal getStartParameter() {
        throw new UnsupportedOperationException();
    }
}
