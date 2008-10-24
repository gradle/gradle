/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.api.dependencies.maven;

import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.apache.maven.artifact.ant.RemoteRepository;
import org.gradle.api.dependencies.maven.MavenPom;
import org.gradle.api.dependencies.maven.PublishFilter;

import java.io.File;
import java.util.List;

/**
 * <p>A resolver that can only be used for uploading artifacts to a Maven repository. If you use this resolver for getting
 * dependencies from a Maven repository, an exception is thrown. This resolver support all aspects of Maven deployment,
 * including snapshot deployment and metadata.xml manipulation.</p>
 * <p/>
 * <p>You have to specify at least one repository. Otherwise, if there is only one artifact, usually there is not more to do.
 * If there is more than one artifact you have to decide what to do about this, as a Maven pom can only deal with one artifact.
 * There are two strategies. If you want to deploy only one artifact you have to specify a filter to select this artifact. If you
 * want to deploy more than one artifact, you have to specify filters which select each artifact. Associated with each filter is
 * a separate configurable pom.</p>
 *
 * <p>You can create an instance of this type via the {@link org.gradle.api.tasks.Upload#uploadResolvers} container</p> 
 *
 * @author Hans Dockter
 */
public interface MavenUploader extends DependencyResolver {
    String DEFAULT_ARTIFACT_POM_NAME = "default";

    RemoteRepository getRepository();

    void setRepository(RemoteRepository repository);

    RemoteRepository getSnapshotRepository();

    void setSnapshotRepository(RemoteRepository snapshotRepository);

    /**
     * Returns the default filter being used. .
     *
     * @see #setFilter(PublishFilter)
     */
    PublishFilter getFilter();

    /**
     * <p>Sets the default filter to be used. This filter is active if no custom filters have been added (see {@link #addFilter(String, PublishFilter)}).
     * If at least one custom filter has been added the default filter is not used any longer.</p>
     * <p>The default for this property is {@link org.gradle.api.dependencies.maven.PublishFilter#ALWAYS_ACCEPT}.
     * If there is only one artifact you are fine with this filter. If there is more than one artifact, deployment will lead to
     * an exception, if you don't specify a filter that selects the artifact to deploy. If you want to deploy more than one artiact you have
     * to use the (see {@link #addFilter(String, PublishFilter)} method.</p>
     *
     * @param defaultFilter
     * @see #getFilter()
     */
    void setFilter(PublishFilter defaultFilter);

    /**
     * Those properties allow you to specify a filter to select the artifact to deploy. By default the filter accepts any artifact.
     * The pom property can be used to customize the pom generation. By default the properties of such a pom object are all null.
     * Null means that Gradle will use default values for generating the Maven pom. Those default values are derived from the deployable artifact
     * and from the project type (e.g. java, war, ...). If you explicitly set a pom property, Gradle will use those instead.
     *
     * @return
     */
    MavenPom getPom();

    /**
     * <p>Sets the default pom to be used. This pom is active if no custom filters have been added (see {@link #addFilter(String, PublishFilter)}).
     * If at least one custom filter has been added the default pom is not used any longer.</p>
     * <p>Usually you don't need to set this property as the default value provides you a pom object you might use for configuration.
     * By default the properties of such a pom object are all null.
     * If they are null, Gradle will use default values for generating the Maven pom. Those default values are derived from the deployable artifact
     * and from the project type (e.g. java, war, ...). If you explicitly set a pom property, Gradle will use this instead.</p>
     *
     * @param defaultPom
     */
    void setPom(MavenPom defaultPom);

    void addProtocolProviderJars(List<File> jars);

    /**
     * If you want to deploy more than one artifact you need to define filters to select each of those artifacts. The method
     * returns a pom object associated with this filter, that allows you to customize the pom generation for the artifact selected
     * by the filter.
     *
     * @param name The name of the filter
     * @param publishFilter The filter to use
     * @return The pom associated with the filter
     */
    MavenPom addFilter(String name, PublishFilter publishFilter);

    /**
     * Returns a filter added with {@link #addFilter(String, PublishFilter)} 
     *
     * @param name The name of the filter
     */
    PublishFilter filter(String name);

    /**
     * Returns the pom associated with a filter added with {@link #addFilter(String, PublishFilter)}.
     *
     * @param name The name of the filter.
     */
    MavenPom pom(String name);
}
