/*
 * Copyright 2016 the original author or authors.
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

import com.google.common.base.Preconditions;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.JvmConstants;
import org.gradle.api.internal.tasks.compile.CompilationSourceDirs;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.internal.jvm.JavaModuleDetector;
import org.gradle.internal.xml.XmlTransformer;
import org.gradle.plugins.ide.api.XmlFileContentMerger;
import org.gradle.plugins.ide.eclipse.model.internal.ClasspathFactory;
import org.gradle.plugins.ide.eclipse.model.internal.FileReferenceFactory;
import org.gradle.plugins.ide.internal.IdeArtifactRegistry;
import org.gradle.plugins.ide.internal.resolver.DefaultGradleApiSourcesResolver;
import org.gradle.util.internal.ConfigureUtil;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The build path settings for the generated Eclipse project. Used by the
 * {@link org.gradle.plugins.ide.eclipse.GenerateEclipseClasspath} task to generate an Eclipse .classpath file.
 * <p>
 * The following example demonstrates the various configuration options.
 * Keep in mind that all properties have sensible defaults; only configure them explicitly
 * if the defaults don't match your needs.
 *
 * <pre class='autoTested'>
 * plugins {
 *     id 'java'
 *     id 'eclipse'
 * }
 *
 * configurations {
 *   provided
 *   someBoringConfig
 * }
 *
 * eclipse {
 *   //if you want parts of paths in resulting file to be replaced by variables (files):
 *   pathVariables 'GRADLE_HOME': file('/best/software/gradle'), 'TOMCAT_HOME': file('../tomcat')
 *
 *   classpath {
 *     //you can tweak the classpath of the Eclipse project by adding extra configurations:
 *     plusConfigurations += [ configurations.provided ]
 *
 *     //you can also remove configurations from the classpath:
 *     minusConfigurations += [ configurations.someBoringConfig ]
 *
 *     //if you want to append extra containers:
 *     containers 'someFriendlyContainer', 'andYetAnotherContainer'
 *
 *     //customizing the classes output directory:
 *     defaultOutputDir = file('build-eclipse')
 *
 *     //default settings for downloading sources and Javadoc:
 *     downloadSources = true
 *     downloadJavadoc = false
 *
 *     //if you want to expose test classes to dependent projects
 *     containsTestFixtures = true
 *
 *     //customizing which Eclipse source directories should be marked as test
 *     testSourceSets = [sourceSets.test]
 *
 *     //customizing which dependencies should be marked as test on the project's classpath
 *     testConfigurations = [configurations.testCompileClasspath, configurations.testRuntimeClasspath]
 *   }
 * }
 * </pre>
 *
 * For tackling edge cases, users can perform advanced configuration on the resulting XML file.
 * It is also possible to affect the way that the Eclipse plugin merges the existing configuration
 * via beforeMerged and whenMerged closures.
 * <p>
 * The beforeMerged and whenMerged closures receive a {@link Classpath} object.
 * <p>
 * Examples of advanced configuration:
 *
 * <pre class='autoTested'>
 * plugins {
 *     id 'java'
 *     id 'eclipse'
 * }
 *
 * eclipse {
 *   classpath {
 *     file {
 *       //if you want to mess with the resulting XML in whatever way you fancy
 *       withXml {
 *         def node = it.asNode()
 *         node.appendNode('xml', 'is what I love')
 *       }
 *
 *       //closure executed after .classpath content is loaded from existing file
 *       //but before gradle build information is merged
 *       beforeMerged { classpath -&gt;
 *         //you can tinker with the {@link Classpath} here
 *       }
 *
 *       //closure executed after .classpath content is loaded from existing file
 *       //and after gradle build information is merged
 *       whenMerged { classpath -&gt;
 *         //you can tinker with the {@link Classpath} here
 *       }
 *     }
 *   }
 * }
 * </pre>
 */
public abstract class EclipseClasspath {
    private Iterable<SourceSet> sourceSets;

    private Collection<Configuration> plusConfigurations = new ArrayList<Configuration>();

    private Collection<Configuration> minusConfigurations = new ArrayList<Configuration>();

    private Set<String> containers = new LinkedHashSet<String>();

    private File defaultOutputDir;

    private boolean downloadSources = true;

    private boolean downloadJavadoc;

    private XmlFileContentMerger file = new XmlFileContentMerger(new XmlTransformer());

    private Map<String, File> pathVariables = new HashMap<String, File>();

    private boolean projectDependenciesOnly;

    private List<File> classFolders;

    private final org.gradle.api.Project project;

    private final Property<Boolean> containsTestFixtures;

    private final SetProperty<SourceSet> testSourceSets;
    private final SetProperty<Configuration> testConfigurations;

    @Inject
    public EclipseClasspath(org.gradle.api.Project project) {
        this.project = project;
        this.containsTestFixtures = project.getObjects().property(Boolean.class).convention(false);
        this.testSourceSets = project.getObjects().setProperty(SourceSet.class);
        this.testConfigurations = project.getObjects().setProperty(Configuration.class);
    }

    /**
     * The source sets to be added.
     * <p>
     * See {@link EclipseClasspath} for an example.
     */
    public Iterable<SourceSet> getSourceSets() {
        return sourceSets;
    }

    public void setSourceSets(Iterable<SourceSet> sourceSets) {
        this.sourceSets = sourceSets;
    }

    /**
     * The configurations whose files are to be added as classpath entries.
     * <p>
     * See {@link EclipseClasspath} for an example.
     */
    public Collection<Configuration> getPlusConfigurations() {
        return plusConfigurations;
    }

    public void setPlusConfigurations(Collection<Configuration> plusConfigurations) {
        this.plusConfigurations = plusConfigurations;
    }

    /**
     * The configurations whose files are to be excluded from the classpath entries.
     * <p>
     * See {@link EclipseClasspath} for an example.
     */
    public Collection<Configuration> getMinusConfigurations() {
        return minusConfigurations;
    }

    public void setMinusConfigurations(Collection<Configuration> minusConfigurations) {
        this.minusConfigurations = minusConfigurations;
    }

    /**
     * The classpath containers to be added.
     * <p>
     * See {@link EclipseClasspath} for an example.
     */
    public Set<String> getContainers() {
        return containers;
    }

    public void setContainers(Set<String> containers) {
        this.containers = containers;
    }

    /**
     * The default output directory where Eclipse puts compiled classes.
     * <p>
     * See {@link EclipseClasspath} for an example.
     */
    public File getDefaultOutputDir() {
        return defaultOutputDir;
    }

    public void setDefaultOutputDir(File defaultOutputDir) {
        this.defaultOutputDir = defaultOutputDir;
    }

    /**
     * The base output directory for source sets.
     * <p>
     * See {@link EclipseClasspath} for an example.
     *
     * @since 8.1
     */
    @Incubating
    public abstract Property<File> getBaseSourceOutputDir();

    /**
     * Whether to download and associate source Jars with the dependency Jars. Defaults to true.
     * <p>
     * See {@link EclipseClasspath} for an example.
     */
    public boolean isDownloadSources() {
        return downloadSources;
    }

    public void setDownloadSources(boolean downloadSources) {
        this.downloadSources = downloadSources;
    }

    /**
     * Whether to download and associate Javadoc Jars with the dependency Jars. Defaults to false.
     * <p>
     * See {@link EclipseClasspath} for an example.
     */
    public boolean isDownloadJavadoc() {
        return downloadJavadoc;
    }

    public void setDownloadJavadoc(boolean downloadJavadoc) {
        this.downloadJavadoc = downloadJavadoc;
    }

    /**
     * See {@link #file(Action)}.
     */
    public XmlFileContentMerger getFile() {
        return file;
    }

    public void setFile(XmlFileContentMerger file) {
        this.file = file;
    }

    public Map<String, File> getPathVariables() {
        return pathVariables;
    }

    public void setPathVariables(Map<String, File> pathVariables) {
        this.pathVariables = pathVariables;
    }

    public boolean isProjectDependenciesOnly() {
        return projectDependenciesOnly;
    }

    public void setProjectDependenciesOnly(boolean projectDependenciesOnly) {
        this.projectDependenciesOnly = projectDependenciesOnly;
    }

    public List<File> getClassFolders() {
        return classFolders;
    }

    public void setClassFolders(List<File> classFolders) {
        this.classFolders = classFolders;
    }

    public org.gradle.api.Project getProject() {
        return project;
    }

    /**
     * Further classpath containers to be added.
     * <p>
     * See {@link EclipseClasspath} for an example.
     *
     * @param containers the classpath containers to be added
     */
    public void containers(String... containers) {
        Preconditions.checkNotNull(containers);
        this.containers.addAll(Arrays.asList(containers));
    }

    /**
     * Enables advanced configuration like tinkering with the output XML or affecting the way
     * that the contents of an existing .classpath file is merged with Gradle build information.
     * The object passed to the whenMerged{} and beforeMerged{} closures is of type {@link Classpath}.
     * <p>
     * See {@link EclipseProject} for an example.
     */
    public void file(@DelegatesTo(XmlFileContentMerger.class) Closure closure) {
        ConfigureUtil.configure(closure, file);
    }

    /**
     * Enables advanced configuration like tinkering with the output XML or affecting the way
     * that the contents of an existing .classpath file is merged with Gradle build information.
     * The object passed to the whenMerged{} and beforeMerged{} closures is of type {@link Classpath}.
     * <p>
     * See {@link EclipseProject} for an example.
     *
     * @since 3.5
     */
    public void file(Action<? super XmlFileContentMerger> action) {
        action.execute(file);
    }

    /**
     * Calculates, resolves and returns dependency entries of this classpath.
     */
    public List<ClasspathEntry> resolveDependencies() {
        ProjectInternal projectInternal = (ProjectInternal) this.project;
        IdeArtifactRegistry ideArtifactRegistry = projectInternal.getServices().get(IdeArtifactRegistry.class);
        boolean inferModulePath = false;
        Task javaCompileTask = project.getTasks().findByName(JvmConstants.COMPILE_JAVA_TASK_NAME);
        if (javaCompileTask instanceof JavaCompile) {
            JavaCompile javaCompile = (JavaCompile) javaCompileTask;
            inferModulePath = javaCompile.getModularity().getInferModulePath().get();
            if (inferModulePath) {
                List<File> sourceRoots = CompilationSourceDirs.inferSourceRoots((FileTreeInternal) javaCompile.getSource());
                inferModulePath = JavaModuleDetector.isModuleSource(true, sourceRoots);
            }
        }
        ClasspathFactory classpathFactory = new ClasspathFactory(this, ideArtifactRegistry, new DefaultGradleApiSourcesResolver(projectInternal.newDetachedResolver()), inferModulePath);
        return classpathFactory.createEntries();
    }


    @SuppressWarnings("unchecked")
    public void mergeXmlClasspath(Classpath xmlClasspath) {
        file.getBeforeMerged().execute(xmlClasspath);
        List<ClasspathEntry> entries = resolveDependencies();
        xmlClasspath.configure(entries);
        file.getWhenMerged().execute(xmlClasspath);
    }

    public FileReferenceFactory getFileReferenceFactory() {
        FileReferenceFactory referenceFactory = new FileReferenceFactory();
        pathVariables.forEach((key, value) -> referenceFactory.addPathVariable(key, value));
        return referenceFactory;
    }

    /**
     * Returns {@code true} if the classpath contains test fixture classes that should be visible
     * through incoming project dependencies.
     *
     * @since 6.8
     */
    public Property<Boolean> getContainsTestFixtures() {
        return containsTestFixtures;
    }

    /**
     * Returns the test source sets.
     * <p>
     * The source directories in the returned source sets are marked with the 'test' classpath attribute on the Eclipse classpath.
     * <p>
     * The default value contains the following elements:
     * <ul>
     *     <li>All source sets with names containing the 'test' substring (case ignored)</li>
     *     <li>All source sets defined via the jvm-test-suite DSL</li>
     * </ul>
     *
     * @since 7.5
     */
    @Incubating
    public SetProperty<SourceSet> getTestSourceSets() {
        return testSourceSets;
    }

    /**
     * Returns the test configurations.
     * <p>
     * All resolved dependencies that appear only in the returned dependency configurations are marked with the 'test' classpath attribute on the Eclipse classpath.
     * <p>
     * The default value contains the following elements:
     * <ul>
     *     <li>The compile and runtime configurations of the {@link #testSourceSets}, including the jvm-test-suite source sets</li>
     *     <li>Other configurations with names containing the 'test' substring (case ignored)</li>
     * </ul>
     * <p>
     * Note, that this property should contain resolvable configurations only.
     *
     * @since 7.5
     */
    @Incubating
    public SetProperty<Configuration> getTestConfigurations() {
        return testConfigurations;
    }
}
