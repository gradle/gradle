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

package org.gradle.plugins.ide.eclipse.model

import org.gradle.api.artifacts.Configuration
import org.gradle.api.dsl.ConventionProperty
import org.gradle.plugins.ide.api.XmlFileContentMerger
import org.gradle.plugins.ide.eclipse.model.internal.FileReferenceFactory
import org.gradle.plugins.ide.eclipse.model.internal.WtpComponentFactory
import org.gradle.util.ConfigureUtil

/**
 * Enables fine-tuning wtp component details of the Eclipse plugin
 * <p>
 * Example of use with a blend of all possible properties.
 * Bear in mind that usually you don't have configure them directly because Gradle configures it for free!
 *
 * <pre autoTested=''>
 * apply plugin: 'java'
 * apply plugin: 'war'
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
 *       //you can alter the wb-resource elements. sourceDirs is a ConventionProperty.
 *       //non-existing source dirs won't be added to the component file.
 *       sourceDirs += file('someExtraFolder')
 *
 *       //you can alter the files are to be transformed into dependent-module elements:
 *       plusConfigurations += [ configurations.someInterestingConfiguration ]
 *
 *       //or whose files are to be excluded from dependent-module elements:
 *       minusConfigurations += configurations.anotherConfiguration
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
 * <pre autoTested=''>
 * apply plugin: 'java'
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
 *         beforeMerged { wtpComponent ->
 *           //tinker with {@link WtpComponent} here
 *         }
 *
 *         //closure executed after wtp component file content is loaded from existing file
 *         //and after gradle build information is merged
 *         whenMerged { wtpComponent ->
 *           //you can tinker with the {@link WtpComponent} here
 *         }
 *       }
 *     }
 *   }
 * }
 * </pre>
 */
class EclipseWtpComponent {

    /**
     * {@link ConventionProperty} for the source directories to be transformed into wb-resource elements.
     * <p>
     * For examples see docs for {@link EclipseWtp}
     * <p>
     * Only source dirs that exist will be added to the wtp component file.
     * Non-existing resource directory declarations lead to errors when project is imported into Eclipse.
     */
    Set<File> sourceDirs

    /**
     * The configurations whose files are to be transformed into dependent-module elements with a deploy path of '/'.
     * <p>
     * For examples see docs for {@link EclipseWtp}
     */
    Set<Configuration> rootConfigurations = []

    /**
     * The configurations whose files are to be transformed into dependent-module elements with a deploy path of #libDeployPath.
     * <p>
     * For examples see docs for {@link EclipseWtp}
     */
    Set<Configuration> libConfigurations

    /**
     * Synonym for {@link #libConfigurations}.
     */
    Set<Configuration> getPlusConfigurations() {
        getLibConfigurations()
    }
    void setPlusConfigurations(Set<Configuration> plusConfigurations) {
        setLibConfigurations(plusConfigurations)
    }

    /**
     * The configurations whose files are to be excluded from dependent-module elements.
     * <p>
     * For examples see docs for {@link EclipseWtp}
     */
    Set<Configuration> minusConfigurations

    /**
     * The deploy name to be used.
     * <p>
     * For examples see docs for {@link EclipseWtp}
     */
    String deployName

    /**
     * {@link ConventionProperty} for additional wb-resource elements.
     * <p>
     * For examples see docs for {@link EclipseWtp}
     * <p>
     * Only resources that link to an existing directory ({@code WbResource#sourcePath})
     * will be added to the wtp component file.
     * The reason is that non-existing resource directory declarations
     * lead to errors when project is imported into Eclipse.
     */
    List<WbResource> resources = []

    /**
     * Adds a wb-resource.
     * <p>
     * For examples see docs for {@link EclipseWtp}
     *
     * @param args A map that must contain a deployPath and sourcePath key with corresponding values.
     */
    void resource(Map<String, String> args) {
        resources.add(new WbResource(args.deployPath, args.sourcePath))
    }

    /**
     * Additional property elements.
     * <p>
     * For examples see docs for {@link EclipseWtp}
     */
    List<WbProperty> properties = []

    /**
     * Adds a property.
     * <p>
     * For examples see docs for {@link EclipseWtp}
     *
     * @param args A map that must contain a 'name' and 'value' key with corresponding values.
     */
    void property(Map<String, String> args) {
        properties.add(new WbProperty(args.name, args.value))
    }

   /**
     * The context path for the web application
     * <p>
     * For examples see docs for {@link EclipseWtp}
     */
    String contextPath

    /**
     * The deploy path for classes.
     * <p>
     * For examples see docs for {@link EclipseWtp}
     */
    String classesDeployPath = "/WEB-INF/classes"

    /**
     * The deploy path for libraries.
     * <p>
     * For examples see docs for {@link EclipseWtp}
     */
    String libDeployPath = "/WEB-INF/lib"

    /**
     * Enables advanced configuration like tinkering with the output XML
     * or affecting the way existing wtp component file content is merged with gradle build information
     * <p>
     * The object passed to whenMerged{} and beforeMerged{} closures is of type {@link WtpComponent}
     * <p>
     * For example see docs for {@link EclipseWtpComponent}
     */
    void file(Closure closure) {
        ConfigureUtil.configure(closure, file)
    }

    /**
     * See {@link #file(Closure) }
     */
    final XmlFileContentMerger file

    //********

    final org.gradle.api.Project project

    /**
     * The variables to be used for replacing absolute path in dependent-module elements.
     * <p>
     * For examples see docs for {@link EclipseModel}
     */
    Map<String, File> pathVariables = [:]

    EclipseWtpComponent(org.gradle.api.Project project, XmlFileContentMerger file) {
        this.project = project
        this.file = file
    }

    void mergeXmlComponent(WtpComponent xmlComponent) {
        file.beforeMerged.execute(xmlComponent)
        new WtpComponentFactory().configure(this, xmlComponent)
        file.whenMerged.execute(xmlComponent)
    }

    FileReferenceFactory getFileReferenceFactory() {
        def referenceFactory = new FileReferenceFactory()
        pathVariables.each { name, dir -> referenceFactory.addPathVariable(name, dir) }
        return referenceFactory
    }
}
