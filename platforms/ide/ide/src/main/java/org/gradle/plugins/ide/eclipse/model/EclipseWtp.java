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
import groovy.lang.DelegatesTo;
import org.gradle.api.Action;
import org.gradle.api.model.ObjectFactory;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.xml.XmlTransformer;
import org.gradle.plugins.ide.api.XmlFileContentMerger;
import org.gradle.plugins.ide.internal.IdeDeprecations;

import javax.inject.Inject;

import static org.gradle.util.internal.ConfigureUtil.configure;

/**
 * Enables fine-tuning wtp/wst details of the Eclipse plugin
 * <p>
 * For projects applying the eclipse plugin and either one of the ear or war plugins, this plugin is auto-applied.
 * <p>
 * More interesting examples you will find in docs for {@link EclipseWtpComponent}
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
 *   }
 * }
 *
 * </pre>
 */
public abstract class EclipseWtp {

    private EclipseWtpComponent component;
    @SuppressWarnings("deprecation")
    private EclipseWtpFacet facet;

    /**
     * Injects and returns an instance of {@link ObjectFactory}.
     *
     * @since 4.9
     * @deprecated Will be removed in Gradle 10.
     */
    @Deprecated
    @Inject
    protected abstract ObjectFactory getObjectFactory();

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
    public void component(@DelegatesTo(EclipseWtpComponent.class) Closure action) {
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
     *
     * @deprecated Will be removed in Gradle 10.
     */
    @Deprecated
    public EclipseWtpFacet getFacet() {
        IdeDeprecations.nagDeprecatedProperty(EclipseWtp.class, "facet");
        if (facet == null) {
            facet = DeprecationLogger.whileDisabled(() -> {
                XmlTransformer xmlTransformer = new XmlTransformer();
                xmlTransformer.setIndentation("\t");
                return getObjectFactory().newInstance(EclipseWtpFacet.class, new XmlFileContentMerger(xmlTransformer));
            });
        }
        return facet;
    }

    /**
     * Sets the wtp facet configuration.
     *
     * @deprecated Will be removed in Gradle 10.
     */
    @Deprecated
    public void setFacet(EclipseWtpFacet facet) {
        IdeDeprecations.nagDeprecatedProperty(EclipseWtp.class, "facet");
        this.facet = facet;
    }

    /**
     * Configures wtp facet.
     *
     * @deprecated Will be removed in Gradle 10.
     */
    @Deprecated
    public void facet(@DelegatesTo(EclipseWtpFacet.class) Closure action) {
        IdeDeprecations.nagDeprecatedProperty(EclipseWtp.class, "facet");
        configure(action, DeprecationLogger.whileDisabled(this::getFacet));
    }

    /**
     * Configures wtp facet.
     *
     * @since 3.5
     * @deprecated Will be removed in Gradle 10.
     */
    @Deprecated
    public void facet(Action<? super EclipseWtpFacet> action) {
        IdeDeprecations.nagDeprecatedProperty(EclipseWtp.class, "facet");
        action.execute(DeprecationLogger.whileDisabled(this::getFacet));
    }
}
