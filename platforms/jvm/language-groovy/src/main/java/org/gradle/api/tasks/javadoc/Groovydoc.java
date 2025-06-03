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
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.tasks.GroovydocAntAction;
import org.gradle.api.provider.Property;
import org.gradle.api.resources.TextResource;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SourceTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.file.Deleter;
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * <p>Generates HTML API documentation for Groovy source, and optionally, Java source.
 *
 * <p>This task uses Groovy's Groovydoc tool to generate the API documentation. Please note
 * that the Groovydoc tool has some limitations at the moment. The version of the Groovydoc
 * that is used, is the one from the Groovy dependency defined in the build script.
 */
@CacheableTask
public abstract class Groovydoc extends SourceTask {
    private FileCollection groovyClasspath;

    private FileCollection classpath;

    private File destinationDir;

    private boolean use;

    private boolean noTimestamp = true;

    private boolean noVersionStamp = true;

    private String windowTitle;

    private String docTitle;

    private String header;

    private String footer;

    private TextResource overview;

    private Set<Link> links = new LinkedHashSet<Link>();

    private final Property<GroovydocAccess> access = getProject().getObjects().property(GroovydocAccess.class);

    private final Property<Boolean> includeAuthor = getProject().getObjects().property(Boolean.class);

    private final Property<Boolean> processScripts = getProject().getObjects().property(Boolean.class);

    private final Property<Boolean> includeMainForScripts = getProject().getObjects().property(Boolean.class);

    @Inject
    protected abstract WorkerExecutor getWorkerExecutor();

    @TaskAction
    protected void generate() {
        checkGroovyClasspathNonEmpty(getGroovyClasspath().getFiles());
        File destinationDir = getDestinationDir();
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

        getWorkerExecutor().classLoaderIsolation().submit(GroovydocAntAction.class, parameters -> {
            parameters.getAntLibraryClasspath().from(getClasspath());
            parameters.getAntLibraryClasspath().from(getGroovyClasspath());
            parameters.getSource().convention(getSource());
            parameters.getDestinationDirectory().fileValue(destinationDir);
            parameters.getUse().convention(isUse());
            parameters.getNoTimestamp().convention(isNoTimestamp());
            parameters.getNoVersionStamp().convention(isNoVersionStamp());
            parameters.getWindowTitle().convention(getWindowTitle());
            parameters.getDocTitle().convention(getDocTitle());
            parameters.getHeader().convention(getHeader());
            parameters.getFooter().convention(getFooter());
            parameters.getOverview().convention(getPathToOverview());
            parameters.getAccess().convention(getAccess());
            parameters.getLinks().convention(getLinks());
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
     */
    @OutputDirectory
    @ToBeReplacedByLazyProperty
    public File getDestinationDir() {
        return destinationDir;
    }

    /**
     * Sets the directory to generate the documentation into.
     */
    public void setDestinationDir(File destinationDir) {
        this.destinationDir = destinationDir;
    }

    /**
     * Returns the classpath containing the Groovy library to be used.
     *
     * @return The classpath containing the Groovy library to be used
     */
    @Classpath
    @ToBeReplacedByLazyProperty
    public FileCollection getGroovyClasspath() {
        return groovyClasspath;
    }

    /**
     * Sets the classpath containing the Groovy library to be used.
     */
    public void setGroovyClasspath(FileCollection groovyClasspath) {
        this.groovyClasspath = groovyClasspath;
    }

    /**
     * Returns the classpath used to locate classes referenced by the documented sources.
     *
     * @return The classpath used to locate classes referenced by the documented sources
     */
    @Classpath
    @ToBeReplacedByLazyProperty
    public FileCollection getClasspath() {
        return classpath;
    }

    /**
     * Sets the classpath used to locate classes referenced by the documented sources.
     */
    public void setClasspath(FileCollection classpath) {
        this.classpath = classpath;
    }

    /**
     * Returns whether to create class and package usage pages.
     */
    @Input
    @ToBeReplacedByLazyProperty
    public boolean isUse() {
        return use;
    }

    /**
     * Sets whether to create class and package usage pages.
     */
    public void setUse(boolean use) {
        this.use = use;
    }

    /**
     * Returns whether to include timestamp within hidden comment in generated HTML (Groovy &gt;= 2.4.6).
     */
    @Input
    @ToBeReplacedByLazyProperty
    public boolean isNoTimestamp() {
        return noTimestamp;
    }

    /**
     * Sets whether to include timestamp within hidden comment in generated HTML (Groovy &gt;= 2.4.6).
     */
    public void setNoTimestamp(boolean noTimestamp) {
        this.noTimestamp = noTimestamp;
    }

    /**
     * Returns whether to include version stamp within hidden comment in generated HTML (Groovy &gt;= 2.4.6).
     */
    @Input
    @ToBeReplacedByLazyProperty
    public boolean isNoVersionStamp() {
        return noVersionStamp;
    }

    /**
     * Sets whether to include version stamp within hidden comment in generated HTML (Groovy &gt;= 2.4.6).
     */
    public void setNoVersionStamp(boolean noVersionStamp) {
        this.noVersionStamp = noVersionStamp;
    }

    /**
     * Returns the browser window title for the documentation. Set to {@code null} when there is no window title.
     */
    @Nullable
    @Optional
    @Input
    @ToBeReplacedByLazyProperty
    public String getWindowTitle() {
        return windowTitle;
    }

    /**
     * Sets the browser window title for the documentation.
     *
     * @param windowTitle A text for the windows title
     */
    public void setWindowTitle(@Nullable String windowTitle) {
        this.windowTitle = windowTitle;
    }

    /**
     * Returns the title for the package index(first) page. Set to {@code null} when there is no document title.
     */
    @Nullable
    @Optional
    @Input
    @ToBeReplacedByLazyProperty
    public String getDocTitle() {
        return docTitle;
    }

    /**
     * Sets title for the package index(first) page (optional).
     *
     * @param docTitle the docTitle as HTML
     */
    public void setDocTitle(@Nullable String docTitle) {
        this.docTitle = docTitle;
    }

    /**
     * Returns the HTML header for each page. Set to {@code null} when there is no header.
     */
    @Nullable
    @Optional
    @Input
    @ToBeReplacedByLazyProperty
    public String getHeader() {
        return header;
    }

    /**
     * Sets header text for each page (optional).
     *
     * @param header the header as HTML
     */
    public void setHeader(@Nullable String header) {
        this.header = header;
    }

    /**
     * Returns the HTML footer for each page. Set to {@code null} when there is no footer.
     */
    @Nullable
    @Optional
    @Input
    @ToBeReplacedByLazyProperty
    public String getFooter() {
        return footer;
    }

    /**
     * Sets footer text for each page (optional).
     *
     * @param footer the footer as HTML
     */
    public void setFooter(@Nullable String footer) {
        this.footer = footer;
    }

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
    @ToBeReplacedByLazyProperty
    public Set<Link> getLinks() {
        return Collections.unmodifiableSet(links);
    }

    /**
     * Sets links to groovydoc/javadoc output at the given URL.
     *
     * @param links The links to set
     * @see #link(String, String...)
     */
    public void setLinks(Set<Link> links) {
        this.links = links;
    }

    /**
     * Add links to groovydoc/javadoc output at the given URL.
     *
     * @param url Base URL of external site
     * @param packages list of package prefixes
     */
    public void link(String url, String... packages) {
        links.add(new Link(url, packages));
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
    protected abstract Deleter getDeleter();
}
