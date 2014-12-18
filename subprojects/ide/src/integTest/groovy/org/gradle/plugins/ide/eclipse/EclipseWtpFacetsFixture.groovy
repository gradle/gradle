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

import org.gradle.test.fixtures.file.TestFile

class EclipseWtpFacetsFixture {
    private final TestFile projectDir
    private Node facets

    EclipseWtpFacetsFixture(TestFile projectDir) {
        this.projectDir = projectDir
    }

    private Node getFacets() {
        if (facets == null) {
            TestFile file = projectDir.file(".settings/org.eclipse.wst.common.project.facet.core.xml")
            file.assertIsFile()
            facets = new XmlParser().parse(file)
        }
        return facets
    }

    void assertHasFixedFacets(String... facets) {
        assert getFacets().fixed*.@facet == facets as List
    }

    void assertHasInstalledFacets(String... facets) {
        assert getFacets().installed*.@facet == facets as List
    }

    void assertFacetVersion(String facet, String version) {
        def facetNode = getFacets().installed.find { it.@facet == facet }
        assert facetNode != null
        assert facetNode.@version == version
    }
}
