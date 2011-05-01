/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.util.ConfigureUtil

/**
 * Enables fine-tuning wtp/wst details of the Eclipse plugin
 * <p>
 * Example of use with a blend of all possible properties.
 * Bear in mind that usually you don't have configure them directly because Gradle configures it for free!
 *
 * <pre autoTested=''>
 * apply plugin: 'java'
 * apply plugin: 'war'
 * apply plugin: 'eclipse'
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
 *       sourceDirs += file('someExtraFolder')
 *
 *       //you can alter the files are to be transformed into dependent-module elements:
 *       plusConfigurations += configurations.someInterestingConfiguration
 *
 *       //or whose files are to be excluded from dependent-module elements:
 *       minusConfigurations += configurations.anotherConfiguration
 *
 *       //you can add a wb-resource elements; mandatory keys: 'sourcePath', 'deployPath':
 *       resource sourcePath: 'extra/resource', deployPath: 'deployment/resource'
 *
 *       //you can add a wb-property elements; mandatory keys: 'name', 'value':
 *       property name: 'moodOfTheDay', value: ':-D'
 *
 *       file {
 *         //if you want to mess with the resulting xml in whatever way you fancy
 *         withXml {
 *           def node = it.asNode()
 *           node.appendNode('xml', 'is what I love')
 *         }
 *
 *         //beforeMerged and whenMerged closures are the highest voodoo
 *         //and probably should be used only to solve tricky edge cases.
 *         //the type passed to the closures is {@link WtpComponent}
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
 *
 *     facet {
 *       //you can add some extra wtp facets; mandatory keys: 'name', 'version':
 *       facet name: 'someCoolFacet', version: '1.3'
 *
 *       file {
 *         //if you want to mess with the resulting xml in whatever way you fancy
 *         withXml {
 *           def node = it.asNode()
 *           node.appendNode('xml', 'is what I love')
 *         }
 *
 *         //beforeMerged and whenMerged closures are the highest voodoo for the tricky edge cases.
 *         //the type passed to the closures is {@link WtpFacet}
 *
 *         //closure executed after wtp facet file content is loaded from existing file
 *         //but before gradle build information is merged
 *         beforeMerged { wtpFacet ->
 *           //tinker with {@link WtpFacet} here
 *         }
 *
 *         //closure executed after wtp facet file content is loaded from existing file
 *         //and after gradle build information is merged
 *         whenMerged { wtpFacet ->
 *           //you can tinker with the {@link WtpFacet} here
 *         }
 *       }
 *     }
 *   }
 * }
 *
 * </pre>
 *
 * @author: Szczepan Faber, created at: 4/19/11
 */
class EclipseWtp {

    EclipseWtpComponent component
    EclipseWtpFacet facet

    /**
     * Configures wtp component.
     * <p>
     * For examples see docs for {@link EclipseWtp}
     *
     * @param action
     */
    void component(Closure action) {
        ConfigureUtil.configure(action, component)
    }

    /**
     * Configures wtp facet.
     * <p>
     * For examples see docs for {@link EclipseWtp}
     *
     * @param action
     */
    void facet(Closure action) {
        ConfigureUtil.configure(action, facet)
    }
}
