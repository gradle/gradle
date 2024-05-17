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

import groovy.xml.XmlParser
import org.gradle.test.fixtures.file.TestFile

class EclipseWtpFacetsFixture {
    private final Node facets

    private EclipseWtpFacetsFixture(Node facets) {
        this.facets = facets
    }

    static EclipseWtpFacetsFixture create(TestFile projectDir) {
        TestFile file = projectDir.file(".settings/org.eclipse.wst.common.project.facet.core.xml")
        file.assertIsFile()
        return new EclipseWtpFacetsFixture(new XmlParser().parse(file))
    }

    void assertHasFixedFacets(String... facets) {
        assert this.facets.fixed*.@facet == facets as List
    }

    void assertHasInstalledFacets(String... facets) {
        assert this.facets.installed*.@facet == facets as List
    }

    void assertFacetVersion(String facet, String version) {
        def facetNode = this.facets.installed.find { it.@facet == facet }
        assert facetNode != null
        assert facetNode.@version == version
    }
}
