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

import org.gradle.plugins.ide.api.XmlFileContentMerger
import org.gradle.util.ConfigureUtil

/**
 * Enables fine-tuning wtp facet details of the Eclipse plugin
 * <p>
 * Advanced configuration closures beforeMerged and whenMerged receive {@link WtpFacet} object as parameter.
 *
 * <pre autoTested=''>
 * apply plugin: 'java'
 * apply plugin: 'war'
 * apply plugin: 'eclipse-wtp'
 *
 * eclipse {
 *   wtp {
 *     facet {
 *       //you can add some extra wtp facets; mandatory keys: 'name', 'version':
 *       facet name: 'someCoolFacet', version: '1.3'
 *
 *       file {
 *         //if you want to mess with the resulting XML in whatever way you fancy
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
 */
class EclipseWtpFacet {

    /**
     * The facets to be added as elements.
     * <p>
     * For examples see docs for {@link EclipseWtpFacet}
     */
    List<Facet> facets = []
    // TODO: What's the difference between fixed and installed facets? Why do we only model the latter?

    /**
     * Adds a facet.
     * <p>
     * For examples see docs for {@link EclipseWtpFacet}
     *
     * @param args A map that must contain a 'name' and 'version' key with corresponding values.
     */
    void facet(Map<String, ?> args) {
        facets << ConfigureUtil.configureByMap(args, new Facet())
    }

    /**
     * Enables advanced configuration like tinkering with the output XML
     * or affecting the way existing wtp facet file content is merged with gradle build information
     * <p>
     * The object passed to whenMerged{} and beforeMerged{} closures is of type {@link WtpFacet}
     * <p>
     *
     * For example see docs for {@link EclipseWtpFacet}
     */
    void file(Closure closure) {
        ConfigureUtil.configure(closure, file)
    }

    /**
     * See {@link #file(Closure) }
     */
    final XmlFileContentMerger file

    //********

    EclipseWtpFacet(XmlFileContentMerger file) {
        this.file = file
    }

    void mergeXmlFacet(WtpFacet xmlFacet) {
        file.beforeMerged.execute(xmlFacet)
        xmlFacet.configure(getFacets())
        file.whenMerged.execute(xmlFacet)
    }
}
