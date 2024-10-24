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
package org.gradle.api.artifacts.repositories;

import org.gradle.api.Action;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;

/**
 * An artifact repository which uses a Maven format to store artifacts and meta-data.
 * <p>
 * Repositories of this type are created by the {@link org.gradle.api.artifacts.dsl.RepositoryHandler#maven(org.gradle.api.Action)} group of methods.
 */
public interface MavenArtifactRepository extends ArtifactRepository, UrlArtifactRepository, AuthenticationSupported, MetadataSupplierAware {

    /**
     * The base URL of this repository. This URL is used to find both POMs and artifact files. You can add additional URLs to use to look for artifact files, such as jars, using {@link
     * #getArtifactUrls()}.
     */
    @Override
    Property<URI> getUrl();

    /**
     * Sets the base URL of this repository. This URL is used to find both POMs and artifact files. You can add additional URLs to use to look for artifact files, such as jars, using {@link
     * #getArtifactUrls()}}.
     *
     * <p>The provided value is evaluated as per {@link org.gradle.api.Project#uri(Object)}. This means, for example, you can pass in a {@code File} object, or a relative path to be evaluated relative
     * to the project directory.
     *
     * @param url The base URL.
     * @deprecated Use {@link #getUrl()} instead.
     */
    @Deprecated
    @Override
    void setUrl(CharSequence url);

    /**
     * Sets the base URL of this repository. This URL is used to find both POMs and artifact files. You can add additional URLs to use to look for artifact files, such as jars, using {@link
     * #getArtifactUrls()}}.
     *
     * <p>The provided value is evaluated as per {@link org.gradle.api.Project#uri(Object)}. This means, for example, you can pass in a {@code File} object, or a relative path to be evaluated relative
     * to the project directory.
     *
     * @param url The base URL.
     * @deprecated Use {@link #getUrl()} instead.
     */
    @Deprecated
    @Override
    void setUrl(File url);

    /**
     * Sets the base URL of this repository. This URL is used to find both POMs and artifact files. You can add additional URLs to use to look for artifact files, such as jars, using {@link
     * #getArtifactUrls()}}.
     *
     * <p>The provided value is evaluated as per {@link org.gradle.api.Project#uri(Object)}. This means, for example, you can pass in a {@code File} object, or a relative path to be evaluated relative
     * to the project directory.
     *
     * @param url The base URL.
     * @deprecated Use {@link #getUrl()} instead.
     */
    @Deprecated
    @Override
    void setUrl(FileSystemLocation url);

    /**
     * Sets the base URL of this repository. This URL is used to find both POMs and artifact files. You can add additional URLs to use to look for artifact files, such as jars, using {@link
     * #getArtifactUrls()}}.
     *
     * <p>The provided value is evaluated as per {@link org.gradle.api.Project#uri(Object)}. This means, for example, you can pass in a {@code File} object, or a relative path to be evaluated relative
     * to the project directory.
     *
     * @param url The base URL.
     * @deprecated Use {@link #getUrl()} instead.
     */
    @Deprecated
    @Override
    void setUrl(Path url);

    /**
     * Sets the base URL of this repository. This URL is used to find both POMs and artifact files. You can add additional URLs to use to look for artifact files, such as jars, using {@link
     * #getArtifactUrls()}}.
     *
     * <p>The provided value is evaluated as per {@link org.gradle.api.Project#uri(Object)}. This means, for example, you can pass in a {@code File} object, or a relative path to be evaluated relative
     * to the project directory.
     *
     * @param url The base URL.
     * @deprecated Use {@link #getUrl()} instead.
     */
    @Deprecated
    @Override
    void setUrl(URL url);
    /**
     * Sets the base URL of this repository. This URL is used to find both POMs and artifact files. You can add additional URLs to use to look for artifact files, such as jars, using {@link
     * #getArtifactUrls()}}.
     *
     * <p>The provided value is evaluated as per {@link org.gradle.api.Project#uri(Object)}. This means, for example, you can pass in a {@code File} object, or a relative path to be evaluated relative
     * to the project directory.
     *
     * @param url The base URL.
     * @deprecated Use {@link #getUrl()} instead.
     */
    @Deprecated
    @Override
    void setUrl(URI url);

    /**
     * Returns the additional URLs to use to find artifact files. Note that these URLs are not used to find POM files.
     *
     * @return The additional URLs. Returns an empty list if there are no such URLs.
     */
    @ReplacesEagerProperty(adapter = MavenArtifactRepositoryAdapter.class)
    SetProperty<URI> getArtifactUrls();

    /**
     * Adds some additional URLs to use to find artifact files. Note that these URLs are not used to find POM files.
     *
     * <p>The provided values are evaluated as per {@link org.gradle.api.Project#uri(Object)}. This means, for example, you can pass in a {@code File} object, or a relative path to be evaluated
     * relative to the project directory.
     *
     * @param urls The URLs to add.
     */
    void artifactUrls(Object... urls);

    /**
     * Configures the metadata sources for this repository. This method will replace any previously configured sources
     * of metadata.
     *
     * @param configureAction the configuration of metadata sources.
     *
     * @since 4.5
     */
    void metadataSources(Action<? super MetadataSources> configureAction);

    /**
     * Returns the current metadata sources configuration for the repository.
     *
     * @since 6.4
     */
    MetadataSources getMetadataSources();

    /**
     * Allows configuring the sources of metadata for a specific repository.
     *
     * @since 4.5
     *
     */
    interface MetadataSources {
        /**
         * Indicates that this repository will contain Gradle metadata.
         */
        void gradleMetadata();

        /**
         * Indicates that this repository will contain Maven POM files.
         * If the POM file contains a marker telling that Gradle metadata exists
         * for this component, Gradle will <i>also</i> look for the Gradle metadata
         * file. Gradle module metadata redirection will not happen if {@code ignoreGradleMetadataRedirection()} has been used.
         */
        void mavenPom();

        /**
         * Indicates that this repository may not contain metadata files,
         * but we can infer it from the presence of an artifact file.
         */
        void artifact();

        /**
         * Indicates that this repository will ignore Gradle module metadata redirection markers found in POM files.
         *
         * @since 5.6
         *
         */
        void ignoreGradleMetadataRedirection();

        /**
         * Indicates if this repository contains Gradle module metadata.
         *
         * @since 6.4
         */
        boolean isGradleMetadataEnabled();

        /**
         * Indicates if this repository contains Maven POM files.
         *
         * @since 6.4
         */
        boolean isMavenPomEnabled();

        /**
         * Indicates if this repository only contains artifacts.
         *
         * @since 6.4
         */
        boolean isArtifactEnabled();

        /**
         * Indicates if this repository ignores Gradle module metadata redirection markers.
         *
         * @since 6.4
         */
        boolean isIgnoreGradleMetadataRedirectionEnabled();
    }

    /**
     * Configures the content of this Maven repository.
     * @param configureAction the configuration action
     *
     * @since 5.1
     */
    void mavenContent(Action<? super MavenRepositoryContentDescriptor> configureAction);
}
