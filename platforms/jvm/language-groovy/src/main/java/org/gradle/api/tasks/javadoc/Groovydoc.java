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

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.file.temp.TemporaryFileProvider;
import org.gradle.api.internal.project.IsolatedAntBuilder;
import org.gradle.api.internal.provider.ProviderApiDeprecationLogger;
import org.gradle.api.internal.tasks.AntGroovydoc;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.resources.TextResource;
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
import org.gradle.internal.file.Deleter;
import org.gradle.internal.instrumentation.api.annotations.NotToBeReplacedByLazyProperty;
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * <p>Generates HTML API documentation for Groovy source, and optionally, Java source.
 *
 * <p>This task uses Groovy's Groovydoc tool to generate the API documentation. Please note
 * that the Groovydoc tool has some limitations at the moment. The version of the Groovydoc
 * that is used, is the one from the Groovy dependency defined in the build script.
 */
@CacheableTask
public abstract class Groovydoc extends SourceTask {

    private AntGroovydoc antGroovydoc;
    private FileCollection classpath;

    private TextResource overview;

    private final Property<GroovydocAccess> access = getProject().getObjects().property(GroovydocAccess.class);

    private final Property<Boolean> includeAuthor = getProject().getObjects().property(Boolean.class);

    private final Property<Boolean> processScripts = getProject().getObjects().property(Boolean.class);

    private final Property<Boolean> includeMainForScripts = getProject().getObjects().property(Boolean.class);

    public Groovydoc() {
        getLogging().captureStandardOutput(LogLevel.INFO);
        getUse().convention(false);
        getNoTimestamp().convention(true);
        getNoVersionStamp().convention(true);
    }

    @TaskAction
    protected void generate() {
        checkGroovyClasspathNonEmpty(getGroovyClasspath().getFiles());
        File destinationDir = getDestinationDir().getAsFile().get();
        try {
            getDeleter().ensureEmptyDirectory(destinationDir);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        getAntGroovydoc().execute(
            getSource(), destinationDir, getUse().get(), getNoTimestamp().get(), getNoVersionStamp().get(),
            getWindowTitle().getOrNull(), getDocTitle().getOrNull(), getHeader().getOrNull(), getFooter().getOrNull(),
            getPathToOverview(), getAccess().get(), getLinks().get(), getGroovyClasspath(), getClasspath(),
            getTemporaryDir(), getServices().get(FileSystemOperations.class),
            getIncludeAuthor().get(), getProcessScripts().get(), getIncludeMainForScripts().get()
        );
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
     */
    @OutputDirectory
    @ReplacesEagerProperty
    public abstract DirectoryProperty getDestinationDir();

    /**
     * Returns the classpath containing the Groovy library to be used.
     *
     * @return The classpath containing the Groovy library to be used
     */
    @Classpath
    @ReplacesEagerProperty
    public abstract ConfigurableFileCollection getGroovyClasspath();

    /**
     * Returns the classpath used to locate classes referenced by the documented sources.
     *
     * @return The classpath used to locate classes referenced by the documented sources
     */
    @Classpath
    @ToBeReplacedByLazyProperty(issue = "https://github.com/gradle/gradle/issues/30273")
    public FileCollection getClasspath() {
        return classpath;
    }

    /**
     * Sets the classpath used to locate classes referenced by the documented sources.
     */
    public void setClasspath(FileCollection classpath) {
        this.classpath = classpath;
    }

    @Internal
    @NotToBeReplacedByLazyProperty(because = "Has no lazy replacement")
    public AntGroovydoc getAntGroovydoc() {
        if (antGroovydoc == null) {
            IsolatedAntBuilder antBuilder = getServices().get(IsolatedAntBuilder.class);
            TemporaryFileProvider temporaryFileProvider = getServices().get(TemporaryFileProvider.class);
            antGroovydoc = new AntGroovydoc(antBuilder, temporaryFileProvider);
        }
        return antGroovydoc;
    }

    public void setAntGroovydoc(AntGroovydoc antGroovydoc) {
        this.antGroovydoc = antGroovydoc;
    }

    /**
     * Returns whether to create class and package usage pages.
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
     */
    @Optional
    @Input
    @ReplacesEagerProperty
    public abstract Property<String> getWindowTitle();

    /**
     * Returns the title for the package index(first) page. Set to {@code null} when there is no document title.
     */
    @Optional
    @Input
    @ReplacesEagerProperty
    public abstract Property<String> getDocTitle();

    /**
     * Returns the HTML header for each page. Set to {@code null} when there is no header.
     */
    @Optional
    @Input
    @ReplacesEagerProperty
    public abstract Property<String> getHeader();

    /**
     * Returns the HTML footer for each page. Set to {@code null} when there is no footer.
     */
    @Optional
    @Input
    @ReplacesEagerProperty
    public abstract Property<String> getFooter();

    /**
     * Returns a HTML text to be used for overview documentation. Set to {@code null} when there is no overview text.
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
    public Property<GroovydocAccess> getAccess() {
        return access;
    }

    /**
     * Whether to include author paragraphs.
     *
     * @since 7.5
     */
    @Input
    public Property<Boolean> getIncludeAuthor() {
        return includeAuthor;
    }

    /**
     * Whether to process scripts.
     *
     * @since 7.5
     */
    @Input
    public Property<Boolean> getProcessScripts() {
        return processScripts;
    }

    /**
     * Whether to include main method for scripts.
     *
     * @since 7.5
     */
    @Input
    public Property<Boolean> getIncludeMainForScripts() {
        return includeMainForScripts;
    }

    /**
     * Returns the links to groovydoc/javadoc output at the given URL.
     */
    @Input
    @ReplacesEagerProperty
    public abstract SetProperty<Link> getLinks();

    /**
     * Add links to groovydoc/javadoc output at the given URL.
     *
     * @param url Base URL of external site
     * @param packages list of package prefixes
     */
    public void link(String url, String... packages) {
        getLinks().add(new Link(url, packages));
    }

    /**
     * A Link class represent a link between groovydoc/javadoc output and url.
     */
    public static class Link implements Serializable {
        private List<String> packages = new ArrayList<String>();
        private String url;

        /**
         * Constructs a {@code Link}.
         *
         * @param url Base URL of external site
         * @param packages list of package prefixes
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
         */
        public List<String> getPackages() {
            return Collections.unmodifiableList(packages);
        }

        /**
         * Returns the base url for the external site.
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
    protected Deleter getDeleter() {
        throw new UnsupportedOperationException("Decorator takes care of injection");
    }
}
