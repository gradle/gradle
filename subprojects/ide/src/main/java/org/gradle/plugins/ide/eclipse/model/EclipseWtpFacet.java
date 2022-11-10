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
import groovy.lang.DelegatesTo;
import org.gradle.api.Action;
import org.gradle.plugins.ide.api.XmlFileContentMerger;
import org.gradle.util.internal.ConfigureUtil;

import javax.inject.Inject;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.gradle.util.internal.ConfigureUtil.configure;

/**
 * Enables fine-tuning wtp facet details of the Eclipse plugin
 * <p>
 * Advanced configuration closures beforeMerged and whenMerged receive {@link WtpFacet} object as parameter.
 *
 * <pre class='autoTested'>
 * plugins {
 *     id 'war' // or 'ear' or 'java'
 *     id 'eclipse-wtp'
 * }
 *
 * eclipse {
 *   wtp {
 *     facet {
 *       //you can add some extra wtp facets or update existing ones; mandatory keys: 'name', 'version':
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
 *         beforeMerged { wtpFacet -&gt;
 *           //tinker with {@link WtpFacet} here
 *         }
 *
 *         //closure executed after wtp facet file content is loaded from existing file
 *         //and after gradle build information is merged
 *         whenMerged { wtpFacet -&gt;
 *           //you can tinker with the {@link WtpFacet} here
 *         }
 *       }
 *     }
 *   }
 * }
 *
 * </pre>
 */
public abstract class EclipseWtpFacet {

    private final XmlFileContentMerger file;
    private List<Facet> facets = Lists.newArrayList();

    @Inject
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
    public void file(@DelegatesTo(XmlFileContentMerger.class) Closure closure) {
        configure(closure, file);
    }

    /**
     * Enables advanced configuration like tinkering with the output XML
     * or affecting the way existing wtp facet file content is merged with gradle build information.
     * <p>
     *
     * For example see docs for {@link EclipseWtpFacet}
     *
     * @since 3.5
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
     * If a facet already exists with the given name then its version will be updated.
     * <p>
     * In the case of a "jst.ejb" facet, it will also be added as a fixed facet.
     * <p>
     * For examples see docs for {@link EclipseWtpFacet}
     *
     * @param args A map that must contain a 'name' and 'version' key with corresponding values.
     */
    public void facet(Map<String, ?> args) {
        Facet newFacet = ConfigureUtil.configureByMap(args, new Facet());
        List<Facet> newFacets;
        if ("jst.ejb".equals(newFacet.getName())) {
            newFacets = Arrays.asList(new Facet(Facet.FacetType.fixed, "jst.ejb", null), newFacet);
        } else {
            newFacets = Collections.singletonList(newFacet);
        }
        facets = Lists.newArrayList(Iterables.concat(
            getFacets().stream()
                       .filter(f -> f.getType() != newFacet.getType() || !Objects.equals(f.getName(), newFacet.getName()))
                       .collect(Collectors.toList()),
            newFacets));
    }

    /**
     * Removes incompatible facets from a list of facets.
     * <p>
     * Currently removes the facet "jst.utility" when the facet "jst.ejb" is present.
     *
     * @param facets, a list of facets
     * @return the modified facet list
     */
    List<Facet> replaceInconsistentFacets(List<Facet> facets) {
        if (facets.stream().anyMatch(f -> "jst.ejb".equals(f.getName()))) {
            return facets.stream().filter(f -> !"jst.utility".equals(f.getName())).collect(Collectors.toList());
        }
        return facets;
    }

    @SuppressWarnings("unchecked")
    public void mergeXmlFacet(WtpFacet xmlFacet) {
        file.getBeforeMerged().execute(xmlFacet);
        xmlFacet.configure(replaceInconsistentFacets(getFacets()));
        file.getWhenMerged().execute(xmlFacet);
    }
}
