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
package org.gradle.plugins.eclipse

import org.gradle.api.tasks.AbstractSpockTaskTest
import org.gradle.plugins.eclipse.model.Facet

/**
 * @author Hans Dockter
 */
public class EclipseWtpFacetTest extends AbstractSpockTaskTest {
    private eclipseFacet = createTask(EclipseWtpFacet)

    EclipseWtpFacet getTask() {
        return eclipseFacet
    }

    def "facet should add"() {
        when:
        task.facet name: 'facet1', version: '1.0'
        task.facet name: 'facet2', version: '2.0'

        then:
        task.facets == [new Facet('facet1', '1.0'), new Facet('facet2', '2.0')]
    }
}
