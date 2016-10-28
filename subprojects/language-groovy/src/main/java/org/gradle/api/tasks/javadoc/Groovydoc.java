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
import org.gradle.api.Nullable;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.project.IsolatedAntBuilder;
import org.gradle.api.internal.tasks.AntGroovydoc;
import org.gradle.api.logging.LogLevel;
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

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

// This import must be here due to a clash in Java 8 between this and java.util.Optional.
// Be careful running “Optimize Imports” as it will wipe this out.
// If there's no import below this comment, this has happened.

/**
 * <p>Generates HTML API documentation for Groovy source, and optionally, Java source.
 *
 * <p>This task uses Groovy's Groovydoc tool to generate the API documentation. Please note
 * that the Groovydoc tool has some limitations at the moment. The version of the Groovydoc
 * that is used, is the one from the Groovy dependency defined in the build script.
 */
@CacheableTask
public class Groovydoc extends SourceTask {
    private FileCollection groovyClasspath;

    private FileCollection classpath;

    private File destinationDir;

    private AntGroovydoc antGroovydoc;

    private boolean use;

    private boolean noTimestamp;

    private boolean noVersionStamp;

    private String windowTitle;

    private String docTitle;

    private String header;

    private String footer;

    private TextResource overview;

    private Set<Link> links = new LinkedHashSet<Link>();

    boolean includePrivate;

    public Groovydoc() {
        getLogging().captureStandardOutput(LogLevel.INFO);
    }

    @TaskAction
    protected void generate() {
        checkGroovyClasspathNonEmpty(getGroovyClasspath().getFiles());
        getAntGroovydoc().execute(getSource(), getDestinationDir(), isUse(), isNoTimestamp(), isNoVersionStamp(), getWindowTitle(),
                getDocTitle(), getHeader(), getFooter(), getPathToOverview(), isIncludePrivate(), getLinks(), getGroovyClasspath(),
                getClasspath(), getProject());
    }

    @Nullable @Internal
    private String getPathToOverview() {
        TextResource overview = getOverviewText();
        if (overview!=null) {
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
    public FileTree getSource() {
        return super.getSource();
    }

    /**
     * Returns the directory to generate the documentation into.
     *
     * @return The directory to generate the documentation into
     */
    @OutputDirectory
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
    public AntGroovydoc getAntGroovydoc() {
        if (antGroovydoc == null) {
            IsolatedAntBuilder antBuilder = getServices().get(IsolatedAntBuilder.class);
            ClassPathRegistry classPathRegistry = getServices().get(ClassPathRegistry.class);
            antGroovydoc = new AntGroovydoc(antBuilder, classPathRegistry);
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
     * Returns whether to include timestamp within hidden comment in generated HTML (Groovy >= 2.4.6).
     */
    @Input
    public boolean isNoTimestamp() {
        return noTimestamp;
    }

    /**
     * Sets whether to include timestamp within hidden comment in generated HTML (Groovy >= 2.4.6).
     */
    public void setNoTimestamp(boolean noTimestamp) {
        this.noTimestamp = noTimestamp;
    }

    /**
     * Returns whether to include version stamp within hidden comment in generated HTML (Groovy >= 2.4.6).
     */
    @Input
    public boolean isNoVersionStamp() {
        return noVersionStamp;
    }

    /**
     * Sets whether to include version stamp within hidden comment in generated HTML (Groovy >= 2.4.6).
     */
    public void setNoVersionStamp(boolean noVersionStamp) {
        this.noVersionStamp = noVersionStamp;
    }

    /**
     * Returns the browser window title for the documentation. Set to {@code null} when there is no window title.
     */
    @Input
    @Optional
    public String getWindowTitle() {
        return windowTitle;
    }

    /**
     * Sets the browser window title for the documentation.
     *
     * @param windowTitle A text for the windows title
     */
    public void setWindowTitle(String windowTitle) {
        this.windowTitle = windowTitle;
    }

    /**
     * Returns the title for the package index(first) page. Set to {@code null} when there is no document title.
     */
    @Input
    @Optional
    public String getDocTitle() {
        return docTitle;
    }

    /**
     * Sets title for the package index(first) page (optional).
     *
     * @param docTitle the docTitle as HTML
     */
    public void setDocTitle(String docTitle) {
        this.docTitle = docTitle;
    }

    /**
     * Returns the HTML header for each page. Set to {@code null} when there is no header.
     */
    @Input
    @Optional
    public String getHeader() {
        return header;
    }

    /**
     * Sets header text for each page (optional).
     *
     * @param header the header as HTML
     */
    public void setHeader(String header) {
        this.header = header;
    }

    /**
     * Returns the HTML footer for each page. Set to {@code null} when there is no footer.
     */
    @Input
    @Optional
    public String getFooter() {
        return footer;
    }

    /**
     * Sets footer text for each page (optional).
     *
     * @param footer the footer as HTML
     */
    public void setFooter(String footer) {
        this.footer = footer;
    }

    /**
     * Returns a HTML text to be used for overview documentation. Set to {@code null} when there is no overview text.
     */
    @Nested
    @Optional
    public TextResource getOverviewText() {
        return overview;
    }

    /**
     * Sets a HTML text to be used for overview documentation (optional).
     * <p>
     * <b>Example:</b> {@code overviewText = resources.text.fromFile("/overview.html")}
     */
    public void setOverviewText(TextResource overviewText) {
        this.overview = overviewText;
    }

    /**
     * Returns whether to include all classes and members (i.e. including private ones).
     */
    @Input
    public boolean isIncludePrivate() {
        return includePrivate;
    }

    /**
     * Sets whether to include all classes and members (i.e. including private ones) if set to true.
     */
    public void setIncludePrivate(boolean includePrivate) {
        this.includePrivate = includePrivate;
    }

    /**
     * Returns the links to groovydoc/javadoc output at the given URL.
     */
    @Input
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
}
