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

import org.gradle.api.artifacts.Configuration
import org.gradle.plugins.ide.eclipse.model.internal.WtpComponentFactory
import org.gradle.util.ConfigureUtil
import org.gradle.api.dsl.ConventionProperty

/**
 * Enables fine-tuning wtp component details of the Eclipse plugin
 * <p>
 * For examples see docs for {@link EclipseWtp}
 *
 * @author: Szczepan Faber, created at: 4/20/11
 */
class EclipseWtpComponent {

    /**
     * {@link ConventionProperty} for the source directories to be transformed into wb-resource elements.
     * <p>
     * For examples see docs for {@link EclipseWtp}
     */
    Set<File> sourceDirs

    /**
     * The configurations whose files are to be transformed into dependent-module elements.
     * <p>
     * For examples see docs for {@link EclipseWtp}
     */
    Set<Configuration> plusConfigurations

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
        //TODO SF validation
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
        //TODO SF validation
        properties.add(new WbProperty(args.name, args.value))
    }

   /**
     * The context path for the web application
     * <p>
     * For examples see docs for {@link EclipseWtp}
     */
    String contextPath

    /**
     * Enables advanced configuration like tinkering with the output xml
     * or affecting the way existing wtp component file content is merged with gradle build information
     * <p>
     * The object passed to whenMerged{} and beforeMerged{} closures is of type {@link WtpComponent}
     * <p>
     *
     * For example see docs for {@link EclipseWtp}
     */
    void file(Closure closure) {
        ConfigureUtil.configure(closure, file)
    }

    //********

    XmlFileContentMerger file
    org.gradle.api.Project project

    /**
     * The variables to be used for replacing absolute path in dependent-module elements.
     * <p>
     * For examples see docs for {@link EclipseModel}
     */
    Map<String, File> pathVariables = [:]

    void mergeXmlComponent(WtpComponent xmlComponent) {
        file.beforeMerged.execute(xmlComponent)
        new WtpComponentFactory().configure(this, xmlComponent)
        file.whenMerged.execute(xmlComponent)
    }
}
