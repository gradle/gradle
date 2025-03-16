/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.reporting;

import org.gradle.api.ExtensiblePolymorphicDomainObjectContainer;
import org.gradle.api.Incubating;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.internal.file.FileLookup;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty;

import javax.inject.Inject;
import java.io.File;
import java.util.concurrent.Callable;

/**
 * A project extension named "reporting" that provides basic reporting settings and utilities.
 * <p>
 * Example usage:
 * <pre>
 * reporting {
 *     baseDirectory = layout.buildDirectory.dir("our-reports")
 * }
 * </pre>
 * <p>
 * When implementing a task that produces reports, the location of where to generate reports should be obtained
 * via the {@link #file(String)} method of this extension.
 */
public abstract class ReportingExtension {

    /**
     * The name of this extension ("{@value}")
     */
    public static final String NAME = "reporting";

    /**
     * The default name of the base directory for all reports, relative to {@link org.gradle.api.file.ProjectLayout#getBuildDirectory()} ({@value}).
     */
    public static final String DEFAULT_REPORTS_DIR_NAME = "reports";

    private final ProjectInternal project;
    private final DirectoryProperty baseDirectory;
    private final ExtensiblePolymorphicDomainObjectContainer<ReportSpec> reports;

    @Inject
    public ReportingExtension(Project project) {
        this.project = (ProjectInternal)project;
        this.baseDirectory = project.getObjects().directoryProperty();
        this.reports = project.getObjects().polymorphicDomainObjectContainer(ReportSpec.class);
        baseDirectory.set(project.getLayout().getBuildDirectory().dir(DEFAULT_REPORTS_DIR_NAME));
    }

    /**
     * The base directory for all reports
     * <p>
     * This value can be changed, so any files derived from this should be calculated on demand.
     *
     * @return The base directory for all reports
     * @deprecated use {@link #getBaseDirectory()} property instead
     */
    @Deprecated
    public File getBaseDir() {
        logBaseDirDeprecation("getBaseDir()");
        return baseDirectory.getAsFile().get();
    }

    /**
     * Sets the base directory to use for all reports
     *
     * @param baseDir The base directory to use for all reports
     * @since 4.0
     * @deprecated use {@link #getBaseDirectory()} property instead
     */
    @Deprecated
    public void setBaseDir(File baseDir) {
        logBaseDirDeprecation("setBaseDir(File)");
        baseDirectory.set(baseDir);
    }

    /**
     * Sets the base directory to use for all reports
     * <p>
     * The value will be converted to a {@code File} on demand via {@link Project#file(Object)}.
     *
     * @param baseDir The base directory to use for all reports
     * @deprecated use {@link #getBaseDirectory()} property instead
     */
    @Deprecated
    public void setBaseDir(final Object baseDir) {
        logBaseDirDeprecation("setBaseDir(Object)");
        this.baseDirectory.set(project.provider(new Callable<Directory>() {
            @Override
            public Directory call() throws Exception {
                DirectoryProperty result = project.getObjects().directoryProperty();
                result.set(project.file(baseDir));
                return result.get();
            }
        }));
    }

    private static void logBaseDirDeprecation(String methodWithParams) {
        DeprecationLogger.deprecateMethod(ReportingExtension.class, methodWithParams)
            .replaceWith("getBaseDirectory() property")
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(8, "reporting-base-dir")
            .nagUser();
    }

    /**
     * Returns base directory property to use for all reports.
     *
     * @since 4.4
     */
    public DirectoryProperty getBaseDirectory() {
        return baseDirectory;
    }

    /**
     * Creates a file object for the given path, relative to {@link #getBaseDir()}.
     * <p>
     * The reporting base dir can be changed, so users of this method should use it on demand where appropriate.
     *
     * @param path the relative path
     * @return a file object at the given path relative to {@link #getBaseDir()}
     */
    public File file(String path) {  // TODO should this take Object?
        return this.project.getServices().get(FileLookup.class).getFileResolver(getBaseDirectory().getAsFile().get()).resolve(path);
    }

    @ToBeReplacedByLazyProperty
    // TODO this doesn't belong here, that java plugin should add an extension to this guy with this
    public String getApiDocTitle() {
        Object version = project.getVersion();
        if (Project.DEFAULT_VERSION.equals(version)) {
            return project.getName() + " API";
        } else {
            return project.getName() + " " + version + " API";
        }
    }

    /**
     * Container for aggregation reports, which may be configured automatically in reaction to the presence of the jvm-test-suite plugin.
     *
     * @return A container of known aggregation reports
     * @since 7.4
     */
    @Incubating
    public ExtensiblePolymorphicDomainObjectContainer<ReportSpec> getReports() {
        return reports;
    }
}
