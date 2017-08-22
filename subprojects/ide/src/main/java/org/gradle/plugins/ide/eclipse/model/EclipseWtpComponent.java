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
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.plugins.ide.api.XmlFileContentMerger;
import org.gradle.plugins.ide.eclipse.model.internal.FileReferenceFactory;
import org.gradle.plugins.ide.eclipse.model.internal.WtpComponentFactory;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.gradle.util.ConfigureUtil.configure;

/**
 * Enables fine-tuning wtp component details of the Eclipse plugin
 * <p>
 * Example of use with a blend of all possible properties.
 * Bear in mind that usually you don't have to configure them directly because Gradle configures it for free!
 *
 * <pre class='autoTested'>
 * apply plugin: 'war' //or 'ear' or 'java'
 * apply plugin: 'eclipse-wtp'
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
 * For tackling edge cases users can perform advanced configuration on resulting XML file.
 * It is also possible to affect the way eclipse plugin merges the existing configuration
 * via beforeMerged and whenMerged closures.
 * <p>
 * beforeMerged and whenMerged closures receive {@link WtpComponent} object
 * <p>
 * Examples of advanced configuration:
 *
 * <pre class='autoTested'>
 * apply plugin: 'war'
 * apply plugin: 'eclipse-wtp'
 *
 * eclipse {
 *
 *   wtp {
 *     component {
 *       file {
 *         //if you want to mess with the resulting XML in whatever way you fancy
 *         withXml {
 *           def node = it.asNode()
 *           node.appendNode('xml', 'is what I love')
 *         }
 *
 *         //closure executed after wtp component file content is loaded from existing file
 *         //but before gradle build information is merged
 *         beforeMerged { wtpComponent -&gt;
 *           //tinker with {@link WtpComponent} here
 *         }
 *
 *         //closure executed after wtp component file content is loaded from existing file
 *         //and after gradle build information is merged
 *         whenMerged { wtpComponent -&gt;
 *           //you can tinker with the {@link WtpComponent} here
 *         }
 *       }
 *     }
 *   }
 * }
 * </pre>
 */
public class EclipseWtpComponent {

    private final Project project;
    private final XmlFileContentMerger file;

    private Set<File> sourceDirs;
    private Set<Configuration> rootConfigurations = Sets.newHashSet();
    private Set<Configuration> libConfigurations;
    private Set<Configuration> minusConfigurations;
    private String deployName;
    private List<WbResource> resources = Lists.newArrayList();
    private List<WbProperty> properties = Lists.newArrayList();
    private String contextPath;
    private String classesDeployPath = "/WEB-INF/classes";
    private String libDeployPath;
    private Map<String, File> pathVariables = Maps.newHashMap();

    public EclipseWtpComponent(org.gradle.api.Project project, XmlFileContentMerger file) {
        this.project = project;
        this.file = file;
    }

    public Project getProject() {
        return project;
    }

    /**
     * See {@link #file(Action) }
     */
    public XmlFileContentMerger getFile() {
        return file;
    }

    /**
     * Enables advanced configuration like tinkering with the output XML
     * or affecting the way existing wtp component file content is merged with gradle build information
     * <p>
     * The object passed to whenMerged{} and beforeMerged{} closures is of type {@link WtpComponent}
     * <p>
     * For example see docs for {@link EclipseWtpComponent}
     */
    public void file(Closure closure) {
        configure(closure, file);
    }

    /**
     * Enables advanced configuration like tinkering with the output XML
     * or affecting the way existing wtp component file content is merged with gradle build information.
     * <p>
     * For example see docs for {@link EclipseWtpComponent}
     *
     * @since 3.5
     */
    public void file(Action<? super XmlFileContentMerger> action) {
        action.execute(file);
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

    /**
     * The variables to be used for replacing absolute path in dependent-module elements.
     * <p>
     * For examples see docs for {@link EclipseModel}
     */
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

    public void mergeXmlComponent(WtpComponent xmlComponent) {
        file.getBeforeMerged().execute(xmlComponent);
        new WtpComponentFactory(project).configure(this, xmlComponent);
        file.getWhenMerged().execute(xmlComponent);
    }
}
