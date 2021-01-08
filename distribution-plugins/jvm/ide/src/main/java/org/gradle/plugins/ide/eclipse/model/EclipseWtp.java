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

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.model.ObjectFactory;
import org.gradle.internal.xml.XmlTransformer;
import org.gradle.plugins.ide.api.XmlFileContentMerger;

import javax.inject.Inject;

import static org.gradle.util.ConfigureUtil.configure;

/**
 * Enables fine-tuning wtp/wst details of the Eclipse plugin
 * <p>
 * For projects applying the eclipse plugin and either one of the ear or war plugins, this plugin is auto-applied.
 * <p>
 * More interesting examples you will find in docs for {@link EclipseWtpComponent} and {@link EclipseWtpFacet}
 *
 * <pre class='autoTested'>
 * plugins {
 *     id 'war' // or 'ear' or 'java'
 *     id 'eclipse-wtp'
 * }
 *
 * eclipse {
 *
 *   //if you want parts of paths in resulting file(s) to be replaced by variables (files):
 *   pathVariables 'GRADLE_HOME': file('/best/software/gradle'), 'TOMCAT_HOME': file('../tomcat')
 *
 *   wtp {
 *     component {
 *       //for examples see docs for {@link EclipseWtpComponent}
 *     }
 *
 *     facet {
 *       //for examples see docs for {@link EclipseWtpFacet}
 *     }
 *   }
 * }
 *
 * </pre>
 */
public class EclipseWtp {
    private EclipseWtpComponent component;
    private EclipseWtpFacet facet;

    /**
     * Injects and returns an instance of {@link ObjectFactory}.
     *
     * @since 4.9
     */
    @Inject
    protected ObjectFactory getObjectFactory() {
        throw new UnsupportedOperationException();
    }

    /**
     * Configures wtp component.
     * <p>
     * For examples see docs for {@link EclipseWtpComponent}
     */
    public EclipseWtpComponent getComponent() {
        return component;
    }

    public void setComponent(EclipseWtpComponent component) {
        this.component = component;
    }

    /**
     * Configures wtp component.
     * <p>
     * For examples see docs for {@link EclipseWtpComponent}
     */
    public void component(Closure action) {
        configure(action, component);
    }

    /**
     * Configures wtp component.
     * <p>
     * For examples see docs for {@link EclipseWtpComponent}
     *
     * @since 3.5
     */
    public void component(Action<? super EclipseWtpComponent> action) {
        action.execute(component);
    }

    /**
     * Configures wtp facet.
     * <p>
     * For examples see docs for {@link EclipseWtpFacet}
     */
    public EclipseWtpFacet getFacet() {
        if (facet == null) {
            XmlTransformer xmlTransformer = new XmlTransformer();
            xmlTransformer.setIndentation("\t");
            facet = getObjectFactory().newInstance(EclipseWtpFacet.class, new XmlFileContentMerger(xmlTransformer));
        }
        return facet;
    }

    public void setFacet(EclipseWtpFacet facet) {
        this.facet = facet;
    }

    /**
     * Configures wtp facet.
     * <p>
     * For examples see docs for {@link EclipseWtpFacet}
     */
    public void facet(Closure action) {
        configure(action, getFacet());
    }

    /**
     * Configures wtp facet.
     * <p>
     * For examples see docs for {@link EclipseWtpFacet}
     *
     * @since 3.5
     */
    public void facet(Action<? super EclipseWtpFacet> action) {
        action.execute(getFacet());
    }
}
