/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.plugins.ide.eclipse

class EclipseWtpJavaProjectIntegrationTest extends AbstractEclipseIntegrationSpec {
    def "generates configuration files for a Java project"() {
        buildFile << """
apply plugin: 'eclipse-wtp'
apply plugin: 'java'

repositories {
    jcenter()
}

dependencies {
    compile 'com.google.guava:guava:18.0'
    testCompile "junit:junit:4.11"
}
"""

        when:
        run "eclipse"

        then:
        // Builders and natures
        def project = project
        project.assertHasNatures("org.eclipse.jdt.core.javanature",
                "org.eclipse.wst.common.project.facet.core.nature",
                "org.eclipse.wst.common.modulecore.ModuleCoreNature",
                "org.eclipse.jem.workbench.JavaEMFNature"
        )
        project.assertHasBuilders("org.eclipse.jdt.core.javabuilder",
                "org.eclipse.wst.common.project.facet.core.builder",
                "org.eclipse.wst.validation.validationbuilder"
        )

        // TODO - Classpath

        // Facets
        def facets = wtpFacets
        facets.assertHasFixedFacets("jst.java")
        facets.assertHasInstalledFacets("jst.utility", "jst.java")
        facets.assertFacetVersion("jst.utility", "1.0")
        facets.assertFacetVersion("jst.java", "1.7")

        // TODO - Deployment
    }
}
