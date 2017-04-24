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

package org.gradle.testing.performance.generator

/**
 * Generates project dependencies for generated test projects used in performance tests
 *
 * In order to generate realistic project dependencies, we split the projects to layers (numberOfLayers) and generate
 * dependencies to projects in the specified upper layers (maxReferenceHigherLayers) . The sizes of different layers are created so that
 * the first layer is smallest and the size of the following layer is multiplied from the previous layer size (layerRatio).
 * formula used:
 *   total_number_of_projects = x + (x * r) + (x * r^2) + (x * r^3) + ... + (x * r^(n-1))
 *   where r = layer ratio, x = size of first layer, n = number of layers
 * when numberOfProjects = 150, numberOfLayers = 5 and layerRatio = 2.0, this results in layer sizes of 4, 9, 19, 38, 80
 * The last layer size is adjusted so the sum of layer sizes equals the number of projects.
 *
 * The dependencies are picked randomly with a random number generator that is seeded with the project number.
 * This way the function returns the same results each time it is evaluated.
 *
 */
class DependencyGenerator {
    int numberOfProjects
    int numberOfLayers = 5
    double layerRatio = 2.0d
    int numberOfDependencies = 5
    int maxReferenceHigherLayers = 2

    def calculateLayerSizes() {
        assert numberOfProjects >= numberOfLayers

        // distribute projects in layers so that the sizes of subsequent layers are a multiply of the layer ratio

        // total_number_of_projects = x + (x * r) + (x * r^2) + (x * r^3) + ... + (x * r^(n-1))
        // where r = layer ratio, x = size of first layer, n = number of layers

        // solve x "size of first layer" from previous equation
        def multiplierSum = (1..numberOfLayers).collect {
            if ( it == 1) {
                1
            } else {
                layerRatio.power(it - 1)
            }
        }.sum()
        def firstLayerSize = numberOfProjects / multiplierSum

        // calculate layer sizes by using multiplier r^(n-1)*x
        def layerSizes = (1..numberOfLayers).collect {
            if ( it == 1) {
                Math.max(firstLayerSize as int, 1)
            } else {
                Math.max((layerRatio.power(it - 1) * firstLayerSize) as int, 1)
            }
        }

        // adjust the number of projects in last layer to match total number of projects
        def currentSum = layerSizes.sum()
        def difference = Math.abs(currentSum-numberOfProjects)
        if(currentSum > numberOfProjects) {
            layerSizes[-1] -= difference
        } else if (currentSum < numberOfProjects) {
            layerSizes[-1] += difference
        }

        assert layerSizes.sum() == numberOfProjects

        layerSizes
    }

    def splitProjectsInLayers(layerSizes) {
        int currentProjectNumber = 1
        (1..numberOfLayers).collect {
            def maxProjects = layerSizes[it-1]
            (1..maxProjects).collect {
                currentProjectNumber++
            }
        }
    }

    /**
     * Generate dependencies for each sub-project using the method described in the class level javadoc
     *
     * @return map where key is projectNumber, value is a collection of projectNumbers that are the dependencies
     */
    Map<Integer, Collection<Integer>> createDependencies() {
        def layerSizes = calculateLayerSizes()
        def projectsInLayers = splitProjectsInLayers(layerSizes)

        def allProjectDependencies = [:]
        projectsInLayers.eachWithIndex { projectsForLayer, layerIndex ->
            projectsForLayer.each { projectNumber ->
                def resolvedDependencies = []
                def startingLayer = Math.max(layerIndex - maxReferenceHigherLayers, 0)
                if(startingLayer < layerIndex) {
                    def possibleDependencies = (startingLayer..<layerIndex).collect {
                        projectsInLayers[it]
                    }.flatten()
                    // use Random with projectNumber as seed, so that we get same results each time
                    Collections.shuffle(possibleDependencies, new Random(projectNumber as long))
                    resolvedDependencies = possibleDependencies.take(numberOfDependencies).sort()
                }
                allProjectDependencies.put(projectNumber, resolvedDependencies)
            }
        }
        allProjectDependencies
    }
}
