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
package org.gradle.plugins.ide.eclipse.model;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.plugins.ide.eclipse.model.internal.FileReferenceFactory;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * Enables fine-tuning wtp component details of the Eclipse plugin
 * <p>
 * Example of use with a blend of all possible properties.
 * Bear in mind that usually you don't have to configure them directly because Gradle configures it for free!
 *
 * <pre class='autoTested'>
 * plugins {
 *     id 'war' // or 'ear' or 'java'
 *     id 'eclipse-wtp'
 * }
 *
 * configurations {
 *   someInterestingConfiguration
 *   anotherConfiguration
 * }
 *
 * eclipse {
 *
 *   //if you want parts of paths in resulting file(s) to be replaced by variables (files):
 *   pathVariables 'GRADLE_HOME': file('/best/software/gradle'), 'TOMCAT_HOME': file('../tomcat')
 *
 *   wtp {
 *     component {
 *       //you can configure the context path:
 *       contextPath = 'someContextPath'
 *
 *       //you can configure the deployName:
 *       deployName = 'killerApp'
 *
 *       //you can alter the wb-resource elements.
 *       //non-existing source dirs won't be added to the component file.
 *       sourceDirs += file('someExtraFolder')
 *
 *       // dependencies to mark as deployable with lib folder deploy path
 *       libConfigurations += [ configurations.someInterestingConfiguration ]
 *
 *       // dependencies to mark as deployable with root folder deploy path
 *       rootConfigurations += [ configurations.someInterestingConfiguration ]
 *
 *       // dependencies to exclude from wtp deployment
 *       minusConfigurations &lt;&lt; configurations.anotherConfiguration
 *
 *       //you can add a wb-resource elements; mandatory keys: 'sourcePath', 'deployPath':
 *       //if sourcePath points to non-existing folder it will *not* be added.
 *       resource sourcePath: 'extra/resource', deployPath: 'deployment/resource'
 *
 *       //you can add a wb-property elements; mandatory keys: 'name', 'value':
 *       property name: 'moodOfTheDay', value: ':-D'
 *     }
 *   }
 * }
 * </pre>
 *
 */
public abstract class EclipseWtpComponent {

    private final Project project;

    private Set<File> sourceDirs;
    private Set<Configuration> rootConfigurations = new LinkedHashSet<>();
    private Set<Configuration> libConfigurations = new LinkedHashSet<>();
    private Set<Configuration> minusConfigurations = new LinkedHashSet<>();
    private String deployName;
    private List<WbResource> resources = new ArrayList<>();
    private List<WbProperty> properties = new ArrayList<>();
    private String contextPath;
    private String classesDeployPath = "/WEB-INF/classes";
    private String libDeployPath;
    private Map<String, File> pathVariables = new HashMap<>();

    @Inject
    public EclipseWtpComponent(org.gradle.api.Project project) {
        this.project = project;
    }

    public Project getProject() {
        return project;
    }

    /**
     * Source directories to be transformed into wb-resource elements.
     * <p>
     * For examples see docs for {@link EclipseWtp}
     * <p>
     * Only source dirs that exist will be added to the wtp component file.
     * Non-existing resource directory declarations lead to errors when project is imported into Eclipse.
     */
    public Set<File> getSourceDirs() {
        return sourceDirs;
    }

    public void setSourceDirs(Set<File> sourceDirs) {
        this.sourceDirs = sourceDirs;
    }

    /**
     * The configurations whose files are to be marked to be deployed with a deploy path of '/'.
     * <p>
     * For examples see docs for {@link EclipseWtp}
     */
    public Set<Configuration> getRootConfigurations() {
        return rootConfigurations;
    }

    public void setRootConfigurations(Set<Configuration> rootConfigurations) {
        this.rootConfigurations = rootConfigurations;
    }

    /**
     * The configurations whose files are to be marked to be deployed with a deploy path of {@link #getLibDeployPath()}.
     * <p>
     * For examples see docs for {@link EclipseWtp}
     */
    public Set<Configuration> getLibConfigurations() {
        return libConfigurations;
    }

    public void setLibConfigurations(Set<Configuration> libConfigurations) {
        this.libConfigurations = libConfigurations;
    }

    /**
     * Synonym for {@link #getLibConfigurations()}.
     */
    public Set<Configuration> getPlusConfigurations() {
        return getLibConfigurations();
    }

    /**
     * Synonym for {@link #setLibConfigurations(Set)}.
     */
    public void setPlusConfigurations(Set<Configuration> plusConfigurations) {
        setLibConfigurations(plusConfigurations);
    }

    /**
     * The configurations whose files are to be excluded from wtp deployment.
     * <p>
     * For examples see docs for {@link EclipseWtp}
     */
    public Set<Configuration> getMinusConfigurations() {
        return minusConfigurations;
    }

    public void setMinusConfigurations(Set<Configuration> minusConfigurations) {
        this.minusConfigurations = minusConfigurations;
    }

    /**
     * The deploy name to be used.
     * <p>
     * For examples see docs for {@link EclipseWtp}
     */
    public String getDeployName() {
        return deployName;
    }

    public void setDeployName(String deployName) {
        this.deployName = deployName;
    }

    /**
     * Additional wb-resource elements.
     * <p>
     * For examples see docs for {@link EclipseWtp}
     * <p>
     * Only resources that link to an existing directory ({@link WbResource#getSourcePath()})
     * will be added to the wtp component file.
     * The reason is that non-existing resource directory declarations
     * lead to errors when project is imported into Eclipse.
     */
    public List<WbResource> getResources() {
        return resources;
    }

    public void setResources(List<WbResource> resources) {
        this.resources = resources;
    }

    /**
     * Adds a wb-resource.
     * <p>
     * For examples see docs for {@link EclipseWtp}
     *
     * @param args A map that must contain a deployPath and sourcePath key with corresponding values.
     */
    public void resource(Map<String, String> args) {
        resources = Lists.newArrayList(Iterables.concat(getResources(), Collections.singleton(new WbResource(args.get("deployPath"), args.get("sourcePath")))));
    }

    /**
     * Additional property elements.
     * <p>
     * For examples see docs for {@link EclipseWtp}
     */
    public List<WbProperty> getProperties() {
        return properties;
    }

    public void setProperties(List<WbProperty> properties) {
        this.properties = properties;
    }

    /**
     * Adds a property.
     * <p>
     * For examples see docs for {@link EclipseWtp}
     *
     * @param args A map that must contain a 'name' and 'value' key with corresponding values.
     */
    public void property(Map<String, String> args) {
        properties = Lists.newArrayList(Iterables.concat(getProperties(), Collections.singleton(new WbProperty(args.get("name"), args.get("value")))));
    }

    /**
     * The context path for the web application
     * <p>
     * For examples see docs for {@link EclipseWtp}
     */
    public String getContextPath() {
        return contextPath;
    }

    public void setContextPath(String contextPath) {
        this.contextPath = contextPath;
    }

    /**
     * The deploy path for classes.
     * <p>
     * For examples see docs for {@link EclipseWtp}
     */
    public String getClassesDeployPath() {
        return classesDeployPath;
    }

    public void setClassesDeployPath(String classesDeployPath) {
        this.classesDeployPath = classesDeployPath;
    }

    /**
     * The deploy path for libraries.
     * <p>
     * For examples see docs for {@link EclipseWtp}
     */
    public String getLibDeployPath() {
        return libDeployPath;
    }

    public void setLibDeployPath(String libDeployPath) {
        this.libDeployPath = libDeployPath;
    }

    public Map<String, File> getPathVariables() {
        return pathVariables;
    }

    public void setPathVariables(Map<String, File> pathVariables) {
        this.pathVariables = pathVariables;
    }

    public FileReferenceFactory getFileReferenceFactory() {
        FileReferenceFactory referenceFactory = new FileReferenceFactory();
        for (Map.Entry<String, File> pathVariable : pathVariables.entrySet()) {
            referenceFactory.addPathVariable(pathVariable.getKey(), pathVariable.getValue());
        }
        return referenceFactory;
    }
}
