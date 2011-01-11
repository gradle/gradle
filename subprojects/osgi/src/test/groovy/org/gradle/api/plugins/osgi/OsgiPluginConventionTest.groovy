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

import spock.lang.Specification
import org.gradle.util.HelperUtil
import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.internal.plugins.osgi.DefaultOsgiManifest
import org.gradle.api.internal.plugins.osgi.OsgiHelper
import org.gradle.api.plugins.JavaBasePlugin

/**
 * @author Hans Dockter
 */
class OsgiPluginConventionTest extends Specification {
    DefaultProject project = HelperUtil.createRootProject()
    OsgiPluginConvention osgiPluginConvention = new OsgiPluginConvention(project)

    def setup() {
        new JavaBasePlugin().apply(project)
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

    void matchesExpectedConfig(DefaultOsgiManifest osgiManifest) {
        OsgiHelper osgiHelper = new OsgiHelper();
        assert osgiManifest.version == osgiHelper.getVersion((String) project.version)
        assert osgiManifest.name == project.archivesBaseName
        assert osgiManifest.symbolicName == osgiHelper.getBundleSymbolicName(project)
    }
}
