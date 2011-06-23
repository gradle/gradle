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

import org.gradle.api.JavaVersion
import org.gradle.plugins.ide.api.XmlFileContentMerger
import org.gradle.util.ConfigureUtil

/**
 * Enables fine-tuning project details (*.ipr file) of the Idea plugin
 * <p>
 * Example of use with a blend of all possible properties.
 * Bear in mind that usually you don't have configure idea module directly because Gradle configures it for free!
 *
 * <pre autoTested=''>
 * apply plugin: 'java'
 * apply plugin: 'idea'
 *
 * idea {
 *   project {
 *     //if you want to set specific java version for the idea project
 *     javaVersion = '1.5'
 *
 *     //you can update the source wildcards
 *     wildcards += '!?*.ruby'
 *
 *     //you can change the modules of the the *.ipr
 *     //modules = project(':someProject').idea.module
 *
 *     //you can change the output file
 *     outputFile = new File(outputFile.parentFile, 'someBetterName.ipr')
 *   }
 * }
 * </pre>
 *
 * For tackling edge cases users can perform advanced configuration on resulting xml file.
 * It is also possible to affect the way idea plugin merges the existing configuration
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
 *
 * @author Szczepan Faber, created at: 4/4/11
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
    JavaVersion javaVersion

    void setJavaVersion(Object javaVersion) {
        this.javaVersion = JavaVersion.toVersion(javaVersion)
    }

    /**
     * The wildcard resource patterns.
     * <p>
     * See the examples in the docs for {@link IdeaProject}
     */
    Set<String> wildcards

    /**
     * Output *.ipr
     * <p>
     * See the examples in the docs for {@link IdeaProject}
     */
    File outputFile

    /**
     * Enables advanced configuration like tinkering with the output xml
     * or affecting the way existing *.ipr content is merged with gradle build information
     * <p>
     * See the examples in the docs for {@link IdeaProject}
     */
    public void ipr(Closure closure) {
        ConfigureUtil.configure(closure, getIpr())
    }

    /**
     * See {@link #ipr(Closure) }
     */
    XmlFileContentMerger ipr

    PathFactory pathFactory

    void mergeXmlProject(Project xmlProject) {
        ipr.beforeMerged.execute(xmlProject)
        def modulePaths = getModules().collect {
            new ModulePath(getPathFactory().relativePath('PROJECT_DIR', it.outputFile))
        }
        xmlProject.configure(modulePaths, getJavaVersion(), getWildcards())
        ipr.whenMerged.execute(xmlProject)
    }
}
