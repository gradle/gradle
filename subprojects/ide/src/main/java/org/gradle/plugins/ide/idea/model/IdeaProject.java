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
package org.gradle.plugins.ide.idea.model;

import com.google.common.collect.Sets;
import groovy.lang.Closure;
import org.gradle.api.Incubating;
import org.gradle.api.JavaVersion;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.internal.composite.CompositeBuildContext;
import org.gradle.composite.internal.CompositeBuildIdeProjectResolver;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.plugins.ide.api.XmlFileContentMerger;
import org.gradle.util.ConfigureUtil;

import java.io.File;
import java.util.List;
import java.util.Set;

/**
 * Enables fine-tuning project details (*.ipr file) of the IDEA plugin.
 * <p>
 * Example of use with a blend of all possible properties.
 * Typically you don't have to configure IDEA module directly because Gradle configures it for you.
 *
 * <pre autoTested=''>
 * import org.gradle.plugins.ide.idea.model.*
 *
 * apply plugin: 'java'
 * apply plugin: 'idea'
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
 *     //modules = project(':someProject').idea.module
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
 * <pre autoTested=''>
 * apply plugin: 'java'
 * apply plugin: 'idea'
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
 *       beforeMerged { project ->
 *         //you can tinker with {@link Project}
 *       }
 *
 *       //closure executed after *.ipr content is loaded from existing file
 *       //and after gradle build information is merged
 *       whenMerged { project ->
 *         //you can tinker with {@link Project}
 *       }
 *     }
 *   }
 * }
 * </pre>
 */
public class IdeaProject {

    private final org.gradle.api.Project project;
    private final XmlFileContentMerger ipr;
    private final CompositeBuildContext compositeContext;
    private final CompositeBuildIdeProjectResolver moduleToProjectMapper;

    private List<IdeaModule> modules;
    private String jdkName;
    private IdeaLanguageLevel languageLevel;
    private JavaVersion targetBytecodeVersion;
    private String vcs;
    private Set<String> wildcards;
    private File outputFile;
    private Set<ProjectLibrary> projectLibraries = Sets.newLinkedHashSet();
    private PathFactory pathFactory;

    public IdeaProject(org.gradle.api.Project project, XmlFileContentMerger ipr) {
        this.project = project;
        this.ipr = ipr;

        ServiceRegistry services = ((ProjectInternal) project).getServices();
        this.compositeContext = services.get(CompositeBuildContext.class);
        this.moduleToProjectMapper = CompositeBuildIdeProjectResolver.from(services);
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
     * See {@link #ipr(Closure) }
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
    public void ipr(Closure<XmlFileContentMerger> closure) {
        ConfigureUtil.configure(closure, getIpr());
    }

    /**
     * The name of the IDEA project. It is a convenience property that returns the name of the output file (without the file extension).
     * In IDEA, the project name is driven by the name of the 'ipr' file.
     */
    public String getName() {
        return getOutputFile().getName().replaceFirst("\\.ipr$", "");
    }

    /**
     * A {@link org.gradle.api.dsl.ConventionProperty} that holds modules for the ipr file.
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
    @Incubating
    public JavaVersion getTargetBytecodeVersion() {
        return targetBytecodeVersion;
    }

    @Incubating
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
    @Incubating
    public String getVcs() {
        return vcs;
    }

    @Incubating
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
        return outputFile;
    }

    public void setOutputFile(File outputFile) {
        this.outputFile = outputFile;
    }

    /**
     * The project-level libraries to be added to the IDEA project.
     */
    @Incubating
    public Set<ProjectLibrary> getProjectLibraries() {
        return projectLibraries;
    }

    @Incubating
    public void setProjectLibraries(Set<ProjectLibrary> projectLibraries) {
        this.projectLibraries = projectLibraries;
    }

    public PathFactory getPathFactory() {
        return pathFactory;
    }

    public void setPathFactory(PathFactory pathFactory) {
        this.pathFactory = pathFactory;
    }

    public void mergeXmlProject(Project xmlProject) {
        ipr.getBeforeMerged().execute(xmlProject);
        xmlProject.configure(getModules(), getJdkName(), getLanguageLevel(), getTargetBytecodeVersion(), getWildcards(), getProjectLibraries(), getVcs());
        includeModulesFromComposite(xmlProject);
        ipr.getWhenMerged().execute(xmlProject);
    }

    private void includeModulesFromComposite(Project xmlProject) {
        PathFactory pathFactory = getPathFactory();
        Set<ProjectComponentIdentifier> projectsInComposite = compositeContext.getAllProjects();
        for (ProjectComponentIdentifier otherProjectId : projectsInComposite) {
            File imlFile = moduleToProjectMapper.buildArtifactFile(otherProjectId, "iml");
            if (imlFile != null) {
                xmlProject.getModulePaths().add(pathFactory.relativePath("PROJECT_DIR", imlFile));
            }
        }
    }
}
