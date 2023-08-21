/*
 * Copyright 2023 the original author or authors.
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
package org.gradle.plugins.ide.idea.model;

import com.google.common.collect.Sets;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.gradle.api.Action;
import org.gradle.api.JavaVersion;
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.api.provider.Provider;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.plugins.ide.IdeWorkspace;
import org.gradle.plugins.ide.api.XmlFileContentMerger;
import org.gradle.plugins.ide.idea.internal.IdeaModuleMetadata;
import org.gradle.plugins.ide.internal.IdeArtifactRegistry;

import javax.inject.Inject;
import java.io.File;
import java.util.List;
import java.util.Set;

import static org.gradle.util.internal.ConfigureUtil.configure;

/**
 * Enables fine-tuning project details (*.ipr file) of the IDEA plugin.
 * <p>
 * Example of use with a blend of all possible properties.
 * Typically you don't have to configure IDEA module directly because Gradle configures it for you.
 *
 * <pre class='autoTested'>
 * import org.gradle.plugins.ide.idea.model.*
 *
 * plugins {
 *     id 'java'
 *     id 'idea'
 * }
 *
 * idea {
 *   project {
 *     //if you want to set specific jdk and language level
 *     jdkName = '1.6'
 *     languageLevel = '1.5'
 *
 *     //you can update the source wildcards
 *     wildcards += '!?*.ruby'
 *
 *     //you can configure the VCS used by the project
 *     vcs = 'Git'
 *
 *     //you can change the modules of the *.ipr
 *     //modules = project(':some-project').idea.module
 *
 *     //you can change the output file
 *     outputFile = new File(outputFile.parentFile, 'someBetterName.ipr')
 *
 *     //you can add project-level libraries
 *     projectLibraries &lt;&lt; new ProjectLibrary(name: "my-library", classes: [new File("path/to/library")])
 *   }
 * }
 * </pre>
 *
 * For tackling edge cases users can perform advanced configuration on resulting XML file.
 * It is also possible to affect the way IDEA plugin merges the existing configuration
 * via beforeMerged and whenMerged closures.
 * <p>
 * beforeMerged and whenMerged closures receive {@link Project} object
 * <p>
 * Examples of advanced configuration:
 *
 * <pre class='autoTested'>
 * plugins {
 *     id 'java'
 *     id 'idea'
 * }
 *
 * idea {
 *   project {
 *     ipr {
 *       //you can tinker with the output *.ipr file before it's written out
 *       withXml {
 *         def node = it.asNode()
 *         node.appendNode('iLove', 'tinkering with the output *.ipr file!')
 *       }
 *
 *       //closure executed after *.ipr content is loaded from existing file
 *       //but before gradle build information is merged
 *       beforeMerged { project -&gt;
 *         //you can tinker with {@link Project}
 *       }
 *
 *       //closure executed after *.ipr content is loaded from existing file
 *       //and after gradle build information is merged
 *       whenMerged { project -&gt;
 *         //you can tinker with {@link Project}
 *       }
 *     }
 *   }
 * }
 * </pre>
 */
public abstract class IdeaProject implements IdeWorkspace {
    private final org.gradle.api.Project project;
    private final XmlFileContentMerger ipr;
    private final ProjectStateRegistry projectPathRegistry;
    private final IdeArtifactRegistry artifactRegistry;

    private List<IdeaModule> modules;
    private String jdkName;
    private IdeaLanguageLevel languageLevel;
    private JavaVersion targetBytecodeVersion;
    private String vcs;
    private Set<String> wildcards = Sets.newLinkedHashSet();
    private RegularFileProperty outputFile;
    private Set<ProjectLibrary> projectLibraries = Sets.newLinkedHashSet();
    private PathFactory pathFactory;

    @Inject
    public IdeaProject(org.gradle.api.Project project, XmlFileContentMerger ipr) {
        this.project = project;
        this.ipr = ipr;

        ServiceRegistry services = ((ProjectInternal) project).getServices();
        this.projectPathRegistry = services.get(ProjectStateRegistry.class);
        this.artifactRegistry = services.get(IdeArtifactRegistry.class);
        this.outputFile = project.getObjects().fileProperty();
    }

    @Override
    public String getDisplayName() {
        return "IDEA project";
    }

    @Override
    public Provider<RegularFile> getLocation() {
        return outputFile;
    }

    /**
     * An owner of this IDEA project.
     * <p>
     * If IdeaProject requires some information from gradle this field should not be used for this purpose.
     */
    public org.gradle.api.Project getProject() {
        return project;
    }

    /**
     * See {@link #ipr(Action) }
     */
    public XmlFileContentMerger getIpr() {
        return ipr;
    }

    /**
     * Enables advanced configuration like tinkering with the output XML
     * or affecting the way existing *.ipr content is merged with Gradle build information.
     * <p>
     * See the examples in the docs for {@link IdeaProject}
     */
    public void ipr(@SuppressWarnings("rawtypes") @DelegatesTo(XmlFileContentMerger.class) Closure closure) {
        configure(closure, ipr);
    }

    /**
     * Enables advanced configuration like tinkering with the output XML
     * or affecting the way existing *.ipr content is merged with Gradle build information.
     * <p>
     * See the examples in the docs for {@link IdeaProject}
     *
     * @since 3.5
     */
    public void ipr(Action<? super XmlFileContentMerger> action) {
        action.execute(ipr);
    }

    /**
     * The name of the IDEA project. It is a convenience property that returns the name of the output file (without the file extension).
     * In IDEA, the project name is driven by the name of the 'ipr' file.
     */
    public String getName() {
        return getOutputFile().getName().replaceFirst("\\.ipr$", "");
    }

    /**
     * Modules for the ipr file.
     * <p>
     * See the examples in the docs for {@link IdeaProject}
     */
    public List<IdeaModule> getModules() {
        return modules;
    }

    public void setModules(List<IdeaModule> modules) {
        this.modules = modules;
    }

    /**
     * The java version used for defining the project sdk.
     * <p>
     * See the examples in the docs for {@link IdeaProject}
     */
    public String getJdkName() {
        return jdkName;
    }

    public void setJdkName(String jdkName) {
        this.jdkName = jdkName;
    }

    /**
     * The default Java language Level to use for this project.
     * <p>
     * Generally, it isn't recommended to change this value. Instead, you are encouraged to set {@code sourceCompatibility} and {@code targetCompatibility}
     * for your Gradle projects which allows you to have full control over language levels in Gradle projects, and means that Gradle and IDEA will use the same
     * settings when compiling.
     * <p>
     * When not explicitly set, this is calculated as the maximum language level for the Idea modules of this Idea project.
     */
    public IdeaLanguageLevel getLanguageLevel() {
        return languageLevel;
    }

    /**
     * Sets the java language level for the project.
     * <p>
     * When explicitly set in the build script, this setting overrides any calculated values for Idea project
     * and Idea module.
     *
     * @since 4.0
     */
    public void setLanguageLevel(IdeaLanguageLevel languageLevel) {
        this.languageLevel = languageLevel;
    }

    /**
     * Sets the java language level for the project.
     * Pass a valid Java version number (e.g. '1.5') or IDEA language level (e.g. 'JDK_1_5').
     * <p>
     * See the examples in the docs for {@link IdeaProject}.
     * <p>
     * When explicitly set in the build script, this setting overrides any calculated values for Idea project
     * and Idea module.
     */
    public void setLanguageLevel(Object languageLevel) {
        this.languageLevel = new IdeaLanguageLevel(languageLevel);
    }

    /**
     * The target bytecode version to use for this project.
     * <p>
     * Generally, it isn't recommended to change this value. Instead, you are encouraged to set {@code sourceCompatibility} and {@code targetCompatibility}
     * for your Gradle projects which allows you to have full control over language levels in Gradle projects, and means that Gradle and IDEA will use the same
     * settings when compiling.
     * <p>
     * When {@code languageLevel} is not explicitly set, this is calculated as the maximum target bytecode version for the Idea modules of this Idea project.
     */
    public JavaVersion getTargetBytecodeVersion() {
        return targetBytecodeVersion;
    }

    public void setTargetBytecodeVersion(JavaVersion targetBytecodeVersion) {
        this.targetBytecodeVersion = targetBytecodeVersion;
    }

    /**
     * The vcs for the project.
     * <p>
     * Values are the same as used in IDEA's “Version Control” preference window (e.g. 'Git', 'Subversion').
     * <p>
     * See the examples in the docs for {@link IdeaProject}.
     */
    public String getVcs() {
        return vcs;
    }

    public void setVcs(String vcs) {
        this.vcs = vcs;
    }

    /**
     * The wildcard resource patterns.
     * <p>
     * See the examples in the docs for {@link IdeaProject}.
     */
    public Set<String> getWildcards() {
        return wildcards;
    }

    public void setWildcards(Set<String> wildcards) {
        this.wildcards = wildcards;
    }

    /**
     * Output *.ipr
     * <p>
     * See the examples in the docs for {@link IdeaProject}.
     */
    public File getOutputFile() {
        return outputFile.get().getAsFile();
    }

    public void setOutputFile(File outputFile) {
        this.outputFile.set(outputFile);
    }

    /**
     * The project-level libraries to be added to the IDEA project.
     */
    public Set<ProjectLibrary> getProjectLibraries() {
        return projectLibraries;
    }

    public void setProjectLibraries(Set<ProjectLibrary> projectLibraries) {
        this.projectLibraries = projectLibraries;
    }

    public PathFactory getPathFactory() {
        return pathFactory;
    }

    public void setPathFactory(PathFactory pathFactory) {
        this.pathFactory = pathFactory;
    }

    @SuppressWarnings("unchecked")
    public void mergeXmlProject(Project xmlProject) {
        ipr.getBeforeMerged().execute(xmlProject);
        xmlProject.configure(getModules(), getJdkName(), getLanguageLevel(), getTargetBytecodeVersion(), getWildcards(), getProjectLibraries(), getVcs());
        configureModulePaths(xmlProject);
        ipr.getWhenMerged().execute(xmlProject);
    }

    private void configureModulePaths(Project xmlProject) {
        ProjectComponentIdentifier thisProjectId = projectPathRegistry.stateFor(project).getComponentIdentifier();
        for (IdeArtifactRegistry.Reference<IdeaModuleMetadata> reference : artifactRegistry.getIdeProjects(IdeaModuleMetadata.class)) {
            BuildIdentifier otherBuildId = reference.getOwningProject().getBuild();
            if (thisProjectId.getBuild().equals(otherBuildId)) {
                // IDEA Module for project in current build: handled via `modules` model elements.
                continue;
            }
            xmlProject.addModulePath(reference.get().getFile());
        }
    }
}
