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
import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.internal.ClosureBackedAction;
import org.gradle.plugins.ide.api.XmlFileContentMerger;
import org.gradle.util.ConfigureUtil;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Enables fine-tuning wtp facet details of the Eclipse plugin
 * <p>
 * Advanced configuration closures beforeMerged and whenMerged receive {@link WtpFacet} object as parameter.
 *
 * <pre autoTested=''>
 * apply plugin: 'war' //or 'ear' or 'java'
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

public class EclipseWtpFacet {

    private final XmlFileContentMerger file;
    private List<Facet> facets = Lists.newArrayList();

    public EclipseWtpFacet(XmlFileContentMerger file) {
        this.file = file;
    }

    /**
     * See {@link #file(Action) }
     */
    public XmlFileContentMerger getFile() {
        return file;
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
    public void file(Closure closure) {
        file(ClosureBackedAction.of(closure));
    }

    /**
     * Enables advanced configuration like tinkering with the output XML
     * or affecting the way existing wtp facet file content is merged with gradle build information.
     * <p>
     *
     * For example see docs for {@link EclipseWtpFacet}
     */
    public void file(Action<? super XmlFileContentMerger> action) {
        action.execute(file);
    }

    /**
     * The facets to be added as elements.
     * <p>
     * For examples see docs for {@link EclipseWtpFacet}
     */
    public List<Facet> getFacets() {
        return facets;
    }

    public void setFacets(List<Facet> facets) {
        this.facets = facets;
    }

    /**
     * Adds a facet.
     * <p>
     * For examples see docs for {@link EclipseWtpFacet}
     *
     * @param args A map that must contain a 'name' and 'version' key with corresponding values.
     */
    public void facet(Map<String, ?> args) {
        facets = Lists.newArrayList(Iterables.concat(
            getFacets(),
            Collections.singleton(ConfigureUtil.configureByMap(args, new Facet()))
        ));
    }

    public void mergeXmlFacet(WtpFacet xmlFacet) {
        file.getBeforeMerged().execute(xmlFacet);
        xmlFacet.configure(getFacets());
        file.getWhenMerged().execute(xmlFacet);
    }
}
