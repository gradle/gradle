/*
 * Copyright 2009 the original author or authors.
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

import org.gradle.api.Incubating;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.provider.ProviderApiDeprecationLogger;
import org.gradle.api.internal.tasks.GroovydocAntAction;
import org.gradle.api.internal.tasks.GroovydocParameters;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.model.ReplacedBy;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.resources.TextResource;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SourceTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.file.Deleter;
import org.gradle.internal.instrumentation.api.annotations.NotToBeReplacedByLazyProperty;
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty;
import org.gradle.internal.jvm.JpmsConfiguration;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.workers.WorkerExecutor;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>Generates HTML API documentation for Groovy source, and optionally, Java source.
 *
 * <p>This task uses Groovy's Groovydoc tool to generate the API documentation. Please note
 * that the Groovydoc tool has some limitations at the moment. The version of the Groovydoc
 * that is used, is the one from the Groovy dependency defined in the build script.
 * @since 0.7
 */
@CacheableTask
public abstract class Groovydoc extends SourceTask {

    private FileCollection classpath;

    private TextResource overview;

    @Inject
    @SuppressWarnings("this-escape")
    public Groovydoc() {
        getJavaLauncher().convention(getJavaToolchainService().launcherFor(spec -> {}));
        getUse().convention(false);
        getNoTimestamp().convention(true);
        getNoVersionStamp().convention(true);
    }

    @Inject
    protected abstract WorkerExecutor getWorkerExecutor();

    @Inject
    protected abstract JavaToolchainService getJavaToolchainService();

    @Inject
    protected abstract ObjectFactory getObjectFactory();

    /**
     * The Java launcher used to start the worker process for generating Groovydoc.
     *
     * @since 9.7.0
     */
    @Incubating
    @Nested
    public abstract Property<JavaLauncher> getJavaLauncher();

    /**
     * Returns the amount of memory allocated to this task.
     * Ex. 512m, 1G
     *
     * @since 9.7.0
     */
    @Incubating
    @Internal
    public abstract Property<String> getMaxMemory();

    /**
     * Generate.
     *
     * @since 0.8
     */
    @TaskAction
    protected void generate() {
        checkGroovyClasspathNonEmpty(getGroovyClasspath().getFiles());
        File destinationDir = getDestinationDirectory().get().getAsFile();
        try {
            getDeleter().ensureEmptyDirectory(destinationDir);
        } catch (IOException ex) {
            throw UncheckedException.throwAsUncheckedException(ex);
        }
        FileSystemOperations fsOperations = getServices().get(FileSystemOperations.class);

        // Copy all sources into one place
        File tmpDir = getTemporaryDir();
        fsOperations.delete(spec -> spec.delete(tmpDir));
        fsOperations.copy(spec -> spec.from(getSource()).into(tmpDir));

        JavaLauncher launcher = getJavaLauncher().get();
        int javaVersionMajor = launcher.getMetadata().getLanguageVersion().asInt();
        getWorkerExecutor().processIsolation(spec -> {
            spec.getForkOptions().setExecutable(launcher.getExecutablePath().getAsFile().getAbsolutePath());
            spec.getForkOptions().jvmArgs(JpmsConfiguration.forGroovyWorker(javaVersionMajor));
            if (getMaxMemory().isPresent()) {
                spec.getForkOptions().setMaxHeapSize(getMaxMemory().get());
            }
        }).submit(GroovydocAntAction.class, parameters -> {
            parameters.getAntLibraryClasspath().from(getClasspath());
            parameters.getAntLibraryClasspath().from(getGroovyClasspath());
            parameters.getSource().convention(getSource());
            parameters.getDestinationDirectory().fileValue(destinationDir);
            parameters.getUse().convention(getUse());
            parameters.getNoTimestamp().convention(getNoTimestamp());
            parameters.getNoVersionStamp().convention(getNoVersionStamp());
            parameters.getWindowTitle().convention(getWindowTitle());
            parameters.getDocTitle().convention(getDocTitle());
            parameters.getHeader().convention(getHeader());
            parameters.getFooter().convention(getFooter());
            parameters.getJavaVersion().convention(getJavaVersion().map(version -> "JAVA_" + version.asInt()));
            parameters.getShowInternal().convention(getShowInternal());
            parameters.getNoIndex().convention(getNoIndex());
            parameters.getNoDeprecatedList().convention(getNoDeprecatedList());
            parameters.getNoHelp().convention(getNoHelp());
            parameters.getSyntaxHighlighter().convention(getSyntaxHighlighter());
            parameters.getTheme().convention(getTheme());
            parameters.getPreLanguage().convention(getPreLanguage());
            parameters.getAdditionalStylesheets().from(getAdditionalStylesheets());
            parameters.getOverview().convention(getPathToOverview());
            parameters.getAccess().convention(getAccess());
            parameters.getLinks().convention(
                getLinks().get().stream()
                    .map(link -> new GroovydocParameters.Link(link.getPackages(), link.getUrl()))
                    .collect(Collectors.toList())
            );
            parameters.getTmpDir().fileValue(getTemporaryDir());
            parameters.getIncludeAuthor().convention(getIncludeAuthor());
            parameters.getProcessScripts().convention(getProcessScripts());
            parameters.getIncludeMainForScripts().convention(getIncludeMainForScripts());
        });
    }

    @Nullable
    private String getPathToOverview() {
        TextResource overview = getOverviewText();
        if (overview != null) {
            return overview.asFile().getAbsolutePath();
        }
        return null;
    }

    private void checkGroovyClasspathNonEmpty(Collection<File> classpath) {
        if (classpath.isEmpty()) {
            throw new InvalidUserDataException("You must assign a Groovy library to the groovy configuration!");
        }
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
     * Returns the directory to generate the documentation into.
     *
     * @return The directory to generate the documentation into
     *
     * @since 9.7.0
     */
    @Incubating
    @OutputDirectory
    public abstract DirectoryProperty getDestinationDirectory();

    /**
     * Returns the directory to generate the documentation into.
     *
     * @return The directory to generate the documentation into
     * @since 0.7
     */
    @ReplacedBy("destinationDirectory")
    @NotToBeReplacedByLazyProperty(because = "Bridge for backward compatibility, use getDestinationDirectory() instead", willBeDeprecated = true)
    public File getDestinationDir() {
        return getDestinationDirectory().isPresent() ? getDestinationDirectory().get().getAsFile() : null;
    }

    /**
     * Sets the directory to generate the documentation into.
     * @since 0.7
     */
    public void setDestinationDir(File destinationDir) {
        getDestinationDirectory().set(destinationDir);
        getDestinationDirectory().convention(getObjectFactory().directoryProperty().fileValue(destinationDir));
    }

    /**
     * Returns the classpath containing the Groovy library to be used.
     *
     * @return The classpath containing the Groovy library to be used
     * @since 0.7
     */
    @Classpath
    @ReplacesEagerProperty
    public abstract ConfigurableFileCollection getGroovyClasspath();

    /**
     * Returns the classpath used to locate classes referenced by the documented sources.
     *
     * @return The classpath used to locate classes referenced by the documented sources
     * @since 1.0
     */
    @Classpath
    @ToBeReplacedByLazyProperty(issue = "https://github.com/gradle/gradle/issues/30273")
    public FileCollection getClasspath() {
        return classpath;
    }

    /**
     * Sets the classpath used to locate classes referenced by the documented sources.
     * @since 1.0
     */
    public void setClasspath(FileCollection classpath) {
        this.classpath = classpath;
    }

    /**
     * Returns whether to create class and package usage pages.
     * @since 0.8
     */
    @Input
    @ReplacesEagerProperty(originalType = boolean.class)
    public abstract Property<Boolean> getUse();

    @Internal
    @Deprecated
    public Property<Boolean> getIsUse() {
        ProviderApiDeprecationLogger.logDeprecation(getClass(), "getIsUse()", "getUse()");
        return getUse();
    }

    /**
     * Returns whether to include timestamp within hidden comment in generated HTML (Groovy &gt;= 2.4.6).
     * @since 2.13
     */
    @Input
    @ReplacesEagerProperty(originalType = boolean.class)
    public abstract Property<Boolean> getNoTimestamp();

    @Internal
    @Deprecated
    public Property<Boolean> getIsNoTimestamp() {
        ProviderApiDeprecationLogger.logDeprecation(getClass(), "getIsNoTimestamp()", "getNoTimestamp()");
        return getNoTimestamp();
    }

    /**
     * Returns whether to include version stamp within hidden comment in generated HTML (Groovy &gt;= 2.4.6).
     * @since 2.13
     */
    @Input
    @ReplacesEagerProperty(originalType = boolean.class)
    public abstract Property<Boolean> getNoVersionStamp();

    @Internal
    @Deprecated
    public Property<Boolean> getIsNoVersionStamp() {
        ProviderApiDeprecationLogger.logDeprecation(getClass(), "getIsNoVersionStamp()", "getNoVersionStamp()");
        return getNoVersionStamp();
    }

    /**
     * Returns the browser window title for the documentation. Set to {@code null} when there is no window title.
     * @since 0.8
     */
    @Optional
    @Input
    @ReplacesEagerProperty
    public abstract Property<String> getWindowTitle();

    /**
     * Returns the title for the package index(first) page. Set to {@code null} when there is no document title.
     * @since 0.8
     */
    @Optional
    @Input
    @ReplacesEagerProperty
    public abstract Property<String> getDocTitle();

    /**
     * Returns the HTML header for each page. Set to {@code null} when there is no header.
     * @since 0.8
     */
    @Optional
    @Input
    @ReplacesEagerProperty
    public abstract Property<String> getHeader();

    /**
     * Returns the HTML footer for each page. Set to {@code null} when there is no footer.
     * @since 0.8
     */
    @Optional
    @Input
    @ReplacesEagerProperty
    public abstract Property<String> getFooter();

    /**
     * Returns a HTML text to be used for overview documentation. Set to {@code null} when there is no overview text.
     * @since 2.14
     */
    @Nullable
    @Optional
    @Nested
    public TextResource getOverviewText() {
        return overview;
    }

    /**
     * Sets a HTML text to be used for overview documentation (optional).
     * <p>
     * <b>Example:</b> {@code overviewText = resources.text.fromFile("/overview.html")}
     * @since 2.14
     */
    public void setOverviewText(@Nullable TextResource overviewText) {
        this.overview = overviewText;
    }

    /**
     * The most restrictive access level to include in the Groovydoc.
     *
     * <p>
     * For example, to include classes and members with package, protected, and public access, use {@link GroovydocAccess#PACKAGE}.
     * </p>
     *
     * @return the access property
     * @since 7.5
     */
    @Input
    public abstract Property<GroovydocAccess> getAccess();

    /**
     * Whether to include author paragraphs.
     *
     * @since 7.5
     */
    @Input
    public abstract Property<Boolean> getIncludeAuthor();

    /**
     * Whether to process scripts.
     *
     * @since 7.5
     */
    @Input
    public abstract Property<Boolean> getProcessScripts();

    /**
     * Whether to include main method for scripts.
     *
     * @since 7.5
     */
    @Input
    public abstract Property<Boolean> getIncludeMainForScripts();

    /**
     * The Java language version used when parsing Java source files, e.g. {@link JavaLanguageVersion#of(int) JavaLanguageVersion.of(17)}.
     * <p>
     * Groovydoc uses the JavaParser library to read Java sources; this controls the source level it assumes,
     * which is needed for parsing newer Java language constructs (for example, sealed classes require Java 17).
     * When unset, Groovydoc uses the JavaParser library's own default.
     * <p>
     * Only has an effect with Groovy 4.0.27 or later; the option is silently ignored with earlier Groovy versions.
     *
     * @since 9.8.0
     */
    @Incubating
    @Optional
    @Input
    public abstract Property<JavaLanguageVersion> getJavaVersion();

    /**
     * Whether to include members annotated with {@code groovy.transform.Internal} (per GEP-17) in the generated documentation.
     * <p>
     * Defaults to {@code false}, so internal members are hidden. Only has an effect with Groovy 6.0.0 or later;
     * the option is silently ignored with earlier Groovy versions.
     *
     * @since 9.8.0
     */
    @Incubating
    @Input
    public abstract Property<Boolean> getShowInternal();

    /**
     * Whether to suppress generation of the alphabetical index page ({@code index-all.html}) and its nav-bar link.
     * <p>
     * Defaults to {@code false}. Only has an effect with Groovy 6.0.0 or later;
     * the option is silently ignored with earlier Groovy versions.
     *
     * @since 9.8.0
     */
    @Incubating
    @Input
    public abstract Property<Boolean> getNoIndex();

    /**
     * Whether to suppress generation of the deprecated-list page ({@code deprecated-list.html}) and its nav-bar link.
     * <p>
     * Defaults to {@code false}. Only has an effect with Groovy 6.0.0 or later;
     * the option is silently ignored with earlier Groovy versions.
     *
     * @since 9.8.0
     */
    @Incubating
    @Input
    public abstract Property<Boolean> getNoDeprecatedList();

    /**
     * Whether to suppress generation of the help page ({@code help-doc.html}) and its nav-bar link.
     * <p>
     * Defaults to {@code false}. Only has an effect with Groovy 6.0.0 or later;
     * the option is silently ignored with earlier Groovy versions.
     *
     * @since 9.8.0
     */
    @Incubating
    @Input
    public abstract Property<Boolean> getNoHelp();

    /**
     * The client-side syntax highlighter for {@code {@snippet}} and fenced Markdown code blocks.
     * <p>
     * Valid values are {@code "prism"} (bundled) or {@code "none"} (default); any other value is treated as {@code "none"}.
     * Only has an effect with Groovy 6.0.0 or later; the option is silently ignored with earlier Groovy versions.
     *
     * @since 9.8.0
     */
    @Incubating
    @Input
    public abstract Property<String> getSyntaxHighlighter();

    /**
     * The theme lock mode for the generated documentation.
     *
     * <ul>
     *   <li>{@code "auto"} (default) — emit a {@code prefers-color-scheme} media query so each reader sees their OS preference.</li>
     *   <li>{@code "light"} — lock the palette to light regardless of OS.</li>
     *   <li>{@code "dark"} — lock the palette to dark regardless of OS.</li>
     * </ul>
     *
     * Any other value is treated as {@code "auto"}. Only has an effect with Groovy 6.0.0 or later;
     * the option is silently ignored with earlier Groovy versions.
     *
     * @since 9.8.0
     */
    @Incubating
    @Input
    public abstract Property<String> getTheme();

    /**
     * The default language id applied to preformatted code blocks in rendered doc comments that carry no {@code class} attribute.
     * <p>
     * When set (for example, {@code "groovy"}), a post-pass adds {@code class="language-xxx"} to the opening tag of such
     * blocks, enabling syntax highlighting for legacy doc-comment code blocks without touching source files. Blocks that
     * already carry any {@code class} attribute are left alone.
     * <p>
     * Only has an effect with Groovy 6.0.0 or later; the option is silently ignored with earlier Groovy versions.
     *
     * @since 9.8.0
     */
    @Incubating
    @Optional
    @Input
    public abstract Property<String> getPreLanguage();

    /**
     * Additional stylesheets to copy into the generated documentation alongside the default stylesheet, preserving each file's name.
     *
     * <p>Only has an effect with Groovy 6.0.0 or later; the stylesheets are silently ignored with earlier Groovy versions.</p>
     *
     * @since 9.8.0
     */
    @Incubating
    @InputFiles
    @PathSensitive(PathSensitivity.NAME_ONLY)
    public abstract ConfigurableFileCollection getAdditionalStylesheets();

    /**
     * Returns the links to groovydoc/javadoc output at the given URL.
     * @since 0.9
     */
    @Input
    @ReplacesEagerProperty
    public abstract SetProperty<Link> getLinks();

    /**
     * Add links to groovydoc/javadoc output at the given URL.
     *
     * @param url Base URL of external site
     * @param packages list of package prefixes
     * @since 0.9
     */
    public void link(String url, String... packages) {
        getLinks().add(new Link(url, packages));
    }

    /**
     * A Link class represent a link between groovydoc/javadoc output and url.
     * @since 0.9
     */
    public static class Link implements Serializable {
        private List<String> packages = new ArrayList<String>();
        private String url;

        /**
         * Constructs a {@code Link}.
         *
         * @param url Base URL of external site
         * @param packages list of package prefixes
         * @since 0.9
         */
        public Link(String url, String... packages) {
            throwExceptionIfNull(url, "Url must not be null");
            if (packages.length == 0) {
                throw new InvalidUserDataException("You must specify at least one package!");
            }
            for (String aPackage : packages) {
                throwExceptionIfNull(aPackage, "A package must not be null");
            }
            this.packages = Arrays.asList(packages);
            this.url = url;
        }

        private void throwExceptionIfNull(String value, String message) {
            if (value == null) {
                throw new InvalidUserDataException(message);
            }
        }

        /**
         * Returns a list of package prefixes to be linked with an external site.
         * @since 0.9
         */
        public List<String> getPackages() {
            return Collections.unmodifiableList(packages);
        }

        /**
         * Returns the base url for the external site.
         * @since 0.9
         */
        public String getUrl() {
            return url;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            Link link = (Link) o;

            if (packages != null ? !packages.equals(link.packages) : link.packages != null) {
                return false;
            }
            if (url != null ? !url.equals(link.url) : link.url != null) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = packages != null ? packages.hashCode() : 0;
            result = 31 * result + (url != null ? url.hashCode() : 0);
            return result;
        }
    }

    @Inject
    protected abstract Deleter getDeleter();
}
