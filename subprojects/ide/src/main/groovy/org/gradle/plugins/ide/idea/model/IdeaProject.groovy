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

package org.gradle.plugins.ide.idea.model

import org.gradle.api.Incubating
import org.gradle.plugins.ide.api.XmlFileContentMerger
import org.gradle.util.ConfigureUtil

/**
 * Enables fine-tuning project details (*.ipr file) of the IDEA plugin.
 * <p>
 * Example of use with a blend of all possible properties.
 * Typically you don't have configure IDEA module directly because Gradle configures it for you.
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
class IdeaProject {

    /**
     * A {@link org.gradle.api.dsl.ConventionProperty} that holds modules for the ipr file.
     * <p>
     * See the examples in the docs for {@link IdeaProject}
     */
    List<IdeaModule> modules

    /**
     * The java version used for defining the project sdk.
     * <p>
     * See the examples in the docs for {@link IdeaProject}
     */
    String jdkName

    /**
     * The java language level of the project.
     * Pass a valid Java version number (e.g. '1.5') or IDEA language level (e.g. 'JDK_1_5').
     * <p>
     * See the examples in the docs for {@link IdeaProject}.
     */
    IdeaLanguageLevel languageLevel

    void setLanguageLevel(Object languageLevel) {
        this.languageLevel = new IdeaLanguageLevel(languageLevel)
    }

    /**
     * The vcs for the project.
     * <p>
     * Values are the same as used in IDEA's “Version Control” preference window (e.g. 'Git', 'Subversion').
     * <p>
     * See the examples in the docs for {@link IdeaProject}.
     */
    @Incubating
    String vcs

    /**
     * The wildcard resource patterns.
     * <p>
     * See the examples in the docs for {@link IdeaProject}.
     */
    Set<String> wildcards

    /**
     * Output *.ipr
     * <p>
     * See the examples in the docs for {@link IdeaProject}.
     */
    File outputFile

    /**
     * The project-level libraries to be added to the IDEA project.
     */
    @Incubating
    Set<ProjectLibrary> projectLibraries = [] as LinkedHashSet

    /**
     * The name of the IDEA project. It is a convenience property that returns the name of the output file (without the file extension).
     * In IDEA, the project name is driven by the name of the 'ipr' file.
     */
    String getName() {
       getOutputFile().name.replaceFirst(/\.ipr$/, '')
    }

    /**
     * Enables advanced configuration like tinkering with the output XML
     * or affecting the way existing *.ipr content is merged with Gradle build information.
     * <p>
     * See the examples in the docs for {@link IdeaProject}
     */
    public void ipr(Closure closure) {
        ConfigureUtil.configure(closure, getIpr())
    }

    /**
     * See {@link #ipr(Closure) }
     */
    final XmlFileContentMerger ipr

    PathFactory pathFactory

    IdeaProject(XmlFileContentMerger ipr) {
        this.ipr = ipr
    }

    void mergeXmlProject(Project xmlProject) {
        ipr.beforeMerged.execute(xmlProject)
        def modulePaths = getModules().collect {
            getPathFactory().relativePath('PROJECT_DIR', it.outputFile)
        }
        xmlProject.configure(modulePaths, getJdkName(), getLanguageLevel(), getWildcards(), getProjectLibraries(), getVcs())
        ipr.whenMerged.execute(xmlProject)
    }
}
