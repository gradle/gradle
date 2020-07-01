/*
 * Copyright 2015 the original author or authors.
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

package gradlebuild.performance.generator


import spock.lang.Specification
import spock.lang.Unroll


class DependencyGeneratorTest extends Specification {
    def "can deal with 0 projects"() {
        given:
        def dependencyGenerator = new DependencyGenerator()
        def numberOfProjects = 0
        dependencyGenerator.numberOfProjects = numberOfProjects

        when:
        def layerSizes = dependencyGenerator.calculateLayerSizes()

        then:
        layerSizes.isEmpty()

        when:
        def projectsInLayer = dependencyGenerator.splitProjectsInLayers(layerSizes)

        then:
        projectsInLayer.isEmpty()

        when:
        def depInfo = dependencyGenerator.createDependencies()

        then:
        depInfo.dependencies.isEmpty()
        depInfo.layerSizes.isEmpty()
    }

    @Unroll
    def "can generate #num project dependencies"() {
        given:
        def dependencyGenerator = new DependencyGenerator()
        def numberOfProjects = num
        dependencyGenerator.numberOfProjects = numberOfProjects

        when:
        def layerSizes = dependencyGenerator.calculateLayerSizes()

        then:
        layerSizes.size() == distribution.size()
        layerSizes == distribution
        layerSizes.sum() == numberOfProjects

        when:
        def projectsInLayer = dependencyGenerator.splitProjectsInLayers(layerSizes)

        then:
        projectsInLayer.size() == distribution.size()
        projectsInLayer.collect { it.size() }.sum() == numberOfProjects

        when:
        def projectDependencies = dependencyGenerator.createDependencies().dependencies
        def projectDependencies2 = dependencyGenerator.createDependencies().dependencies

        then:
        projectDependencies.size() == numberOfProjects
        projectDependencies.keySet().every { it >= 1 && it <= numberOfProjects }
        projectDependencies.values().every { dependencyList -> dependencyList.every { it >= 1 && it <= numberOfProjects } }
        projectDependencies == projectDependencies2

        where:
        num  | distribution
        1    | [1]
        4    | [2, 2]
        9    | [1, 2, 3, 2, 1]
        50   | [1, 2, 6, 16, 16, 6, 2, 1]
        74   | [2, 3, 8, 24, 24, 8, 3, 2]
        150  | [1, 2, 6, 16, 50, 50, 16, 6, 2, 1]
    }
}
