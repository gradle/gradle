/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.api.tasks;

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.work.DisableCachingByDefault;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;

/**
 * This task is <strong>no longer supported</strong> and will <strong>throw an exception</strong> if you try to use it.
 *
 * @deprecated This class is scheduled for removal in Gradle 9.0. To publish artifacts,
 *      use the {@code maven-publish} or {@code ivy-publish} plugins instead.
 */
@Deprecated
@DisableCachingByDefault(because = "Produces no cacheable output")
public abstract class Upload extends ConventionTask {

    /**
     * Do not use this method, it is for internal use only.
     * @deprecated This class is scheduled for removal in Gradle 9.0, this method <strong>should not be used</strong>.
     */
    @Deprecated
    @Inject
    protected abstract DocumentationRegistry getDocumentationRegistry();

    @TaskAction
    protected void upload() {
        DocumentationRegistry docRegistry = getDocumentationRegistry();
        throw new InvalidUserCodeException(
                "The legacy `Upload` task was removed in Gradle 8. Please use the `maven-publish` or `ivy-publish` plugin instead. " +
                        docRegistry.getDocumentationRecommendationFor("on publishing on maven repositories", "publishing_maven", "publishing_maven") + "\n" +
                        docRegistry.getDocumentationRecommendationFor("on publishing on ivy repositories", "publishing_ivy", "publishing_ivy"));
    }

    /**
     * Do not use this method, it will always return {@code false}.
     * @deprecated This class is scheduled for removal in Gradle 9.0, this method <strong>should not be used</strong>.
     */
    @Input
    @Deprecated
    public boolean isUploadDescriptor() {
        DeprecationLogger.deprecateMethod(Upload.class, "isUploadDescriptor()")
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(7, "upload_task_deprecation")
            .nagUser();
        return false;
    }

    /**
     * Do not use this method, it does nothing.
     * @deprecated This class is scheduled for removal in Gradle 9.0, this method <strong>should not be used</strong>.
     */
    @Deprecated
    @SuppressWarnings("UnusedDeclaration")
    public void setUploadDescriptor(boolean uploadDescriptor) {
        DeprecationLogger.deprecateMethod(Upload.class, "setUploadDescriptor(boolean)")
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(7, "upload_task_deprecation")
            .nagUser();
    }

    /**
     * Do not use this method, it will always return {@code null}.
     * @deprecated This class is scheduled for removal in Gradle 9.0, this method <strong>should not be used</strong>.
     */
    @Internal
    @Nullable
    @Deprecated
    public File getDescriptorDestination() {
        DeprecationLogger.deprecateMethod(Upload.class, "getDescriptorDestination()")
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(7, "upload_task_deprecation")
            .nagUser();
        return null;
    }

    /**
     * Do not use this method, it does nothing.
     * @deprecated This class is scheduled for removal in Gradle 9.0, this method <strong>should not be used</strong>.
     */
    @SuppressWarnings("UnusedDeclaration")
    @Deprecated
    public void setDescriptorDestination(@Nullable File descriptorDestination) {
        DeprecationLogger.deprecateMethod(Upload.class, "setDescriptorDestination(File)")
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(7, "upload_task_deprecation")
            .nagUser();
    }

    /**
     * Do not use this method, it will always return {@code null}.
     * @deprecated This class is scheduled for removal in Gradle 9.0, this method <strong>should not be used</strong>.
     */
    @Internal
    @Nullable
    @Deprecated
    public RepositoryHandler getRepositories() {
        DeprecationLogger.deprecateMethod(Upload.class, "getRepositories()")
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(7, "upload_task_deprecation")
            .nagUser();
        return null;
    }

    /**
     * Do not use this method, it will always return {@code null}.
     * @deprecated This class is scheduled for removal in Gradle 9.0, this method <strong>should not be used</strong>.
     */
    @Internal
    @Nullable
    @Deprecated
    public Configuration getConfiguration() {
        DeprecationLogger.deprecateMethod(Upload.class, "getConfiguration()")
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(7, "upload_task_deprecation")
            .nagUser();
        return null;
    }

    /**
     * Do not use this method, it does nothing.
     * @deprecated This class is scheduled for removal in Gradle 9.0, this method <strong>should not be used</strong>.
     */
    @Deprecated
    @SuppressWarnings("UnusedDeclaration")
    public void setConfiguration(@Nullable Configuration configuration) {
        DeprecationLogger.deprecateMethod(Upload.class, "setConfiguration(Configuration)")
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(7, "upload_task_deprecation")
            .nagUser();
    }

    /**
     * Do not use this method, it will always return {@code null}.
     * @deprecated This class is scheduled for removal in Gradle 9.0, this method <strong>should not be used</strong>.
     */
    @Nullable
    @Deprecated
    @SuppressWarnings({"UnusedDeclaration", "rawtypes"})
    public RepositoryHandler repositories(@Nullable @DelegatesTo(RepositoryHandler.class) Closure configureClosure) {
        DeprecationLogger.deprecateMethod(Upload.class, "repositories(Closure)")
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(7, "upload_task_deprecation")
            .nagUser();
        return null;
    }

    /**
     * Do not use this method, it will always return {@code null}.
     * @deprecated This class is scheduled for removal in Gradle 9.0, this method <strong>should not be used</strong>.
     */
    @Nullable
    @Deprecated
    @SuppressWarnings("UnusedDeclaration")
    public RepositoryHandler repositories(Action<? super RepositoryHandler> configureAction) {
        DeprecationLogger.deprecateMethod(Upload.class, "repositories(Action)")
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(7, "upload_task_deprecation")
            .nagUser();
        return null;
    }

    /**
     * Do not use this method, it must return a non-{@code null} value as an input property to all the task to run,
     * but this value <strong>should not be relied upon</strong> for anything.
     * @deprecated This class is scheduled for removal in Gradle 9.0, this method <strong>should not be used</strong>.
     */
    @PathSensitive(PathSensitivity.NAME_ONLY)
    @InputFiles
    @Deprecated
    public FileCollection getArtifacts() {
        DeprecationLogger.deprecateMethod(Upload.class, "getArtifacts()")
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(7, "upload_task_deprecation")
            .nagUser();
        return getProject().files();
    }
}
