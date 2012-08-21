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
package org.gradle.api.plugins.osgi

import org.gradle.util.HelperUtil
import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.internal.plugins.osgi.DefaultOsgiManifest
import org.gradle.api.internal.plugins.osgi.OsgiHelper
import org.gradle.api.plugins.JavaBasePlugin

import spock.lang.Specification
import spock.lang.Issue

/**
 * @author Hans Dockter
 */
class OsgiPluginConventionTest extends Specification {
    DefaultProject project = HelperUtil.createRootProject()
    OsgiPluginConvention osgiPluginConvention = new OsgiPluginConvention(project)

    def setup() {
        project.plugins.apply(JavaBasePlugin)
    }

    def osgiManifestWithNoClosure() {
        OsgiManifest osgiManifest = osgiPluginConvention.osgiManifest()

        expect:
        matchesExpectedConfig(osgiManifest)
    }

    def osgiManifestWithClosure() {
        OsgiManifest osgiManifest = osgiPluginConvention.osgiManifest {
            description = 'myDescription'    
        }

        expect:
        matchesExpectedConfig(osgiManifest)
        osgiManifest.description == 'myDescription'
    }

    @Issue("GRADLE-1670")
    def "doesn't assume that project version is a String"() {
        project.version =  new Object() {
            String toString() {
                "2.1"
            }
        }
        def manifest = osgiPluginConvention.osgiManifest()

        expect:
        manifest.version == "2.1"
    }

    @Issue("GRADLE-1670")
    def "computes its defaults lazily"() {
        def manifest = osgiPluginConvention.osgiManifest()
        def i = 0
        project.version = "${->++i}"
        project.group = "my.group"
        project.archivesBaseName = "myarchive"

        expect:
        manifest.version == "1"
        manifest.version == "2"
        manifest.name == "myarchive"
        manifest.symbolicName == "my.group.myarchive"

        when:
        project.group = "changed.group"
        project.archivesBaseName = "changedarchive"

        then:
        manifest.name == "changedarchive"
        manifest.symbolicName == "changed.group.changedarchive"
    }

    void matchesExpectedConfig(DefaultOsgiManifest osgiManifest) {
        OsgiHelper osgiHelper = new OsgiHelper();
        assert osgiManifest.version == osgiHelper.getVersion((String) project.version)
        assert osgiManifest.name == project.archivesBaseName
        assert osgiManifest.symbolicName == osgiHelper.getBundleSymbolicName(project)
    }
}
