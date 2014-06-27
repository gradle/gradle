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

import org.gradle.util.ConfigureUtil

/**
 * Enables fine-tuning wtp/wst details of the Eclipse plugin
 * <p>
 * More interesting examples you will find in docs for {@link EclipseWtpComponent} and {@link EclipseWtpFacet}
 *
 * <pre autoTested=''>
 * apply plugin: 'java'
 * apply plugin: 'war'
 * apply plugin: 'eclipse-wtp'
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
class EclipseWtp {

    /**
     * Configures wtp component.
     * <p>
     * For examples see docs for {@link EclipseWtpComponent}
     */
    EclipseWtpComponent component

    /**
     * Configures wtp facet.
     * <p>
     * For examples see docs for {@link EclipseWtpFacet}
     */
    EclipseWtpFacet facet

    /**
     * Configures wtp component.
     * <p>
     * For examples see docs for {@link EclipseWtpComponent}
     *
     * @param action
     */
    void component(Closure action) {
        ConfigureUtil.configure(action, component)
    }

    /**
     * Configures wtp facet.
     * <p>
     * For examples see docs for {@link EclipseWtpFacet}
     *
     * @param action
     */
    void facet(Closure action) {
        ConfigureUtil.configure(action, facet)
    }

    /**
     * @param eclipseClasspath - wtp needs access to classpath
     */
    public EclipseWtp(EclipseClasspath eclipseClasspath) {
        this.eclipseClasspath = eclipseClasspath
    }

    private EclipseClasspath eclipseClasspath
}
