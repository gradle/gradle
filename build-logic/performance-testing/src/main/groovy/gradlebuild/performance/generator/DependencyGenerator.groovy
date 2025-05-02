/*
 * Copyright 2020 the original author or authors.
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
/**
 * Generates project dependencies for generated test projects used in performance tests
 *
 * In order to generate realistic project dependencies, we split the projects to layers and generate
 * dependencies to projects in lower layers. The sizes of different layers are created so that
 * the first and last layers are the smallest and the layers in the middle are the largest.
 *
 * For example when numberOfProjects = 150, 10 layers will be created with sizes of
 * [1, 2, 6, 16, 50, 50, 16, 6, 2, 1]
 *
 * The dependencies are picked randomly with a random number generator that is seeded with the project number.
 * This way the function returns the same results each time it is evaluated.
 */
class DependencyGenerator {
    Integer numberOfProjects
    Integer maxProjectDependencies = 5

    def calculateLayerSizes() {
        Deque<Integer> layerSizes = new ArrayDeque<>()
        recursiveLayers(numberOfProjects, layerSizes)
        layerSizes as List
    }

    def static recursiveLayers(Integer numberOfProjects, Deque<Integer> layerSizes) {
        if (numberOfProjects == 0) {
            return true
        }
        if (numberOfProjects < 0) {
            return false
        }

        Integer middle = numberOfProjects / 3
        if (middle == 0) {
            middle = 1
        }
        if (isEven(middle) && isOdd(numberOfProjects)) {
            middle -= 1
        }
        Integer middleInc = 1
        if (isOdd(numberOfProjects)) {
            middleInc = 2
        }

        while (middle < numberOfProjects) {
            for (v in layerSizes) {
                if (middle >= v) {
                    return false
                }
            }
            def remaining
            if (isEven(numberOfProjects)) {
                remaining = numberOfProjects - middle * 2
                layerSizes.addLast(middle)
                layerSizes.addFirst(middle)
            } else {
                remaining = numberOfProjects - middle
                layerSizes.addLast(middle)
            }

            def result = recursiveLayers(remaining, layerSizes)
            if (result) {
                return true
            } else {
                layerSizes.removeLast()
                if (isEven(numberOfProjects)) {
                    layerSizes.removeFirst()
                }
                middle += middleInc
            }
        }

        if (middle == numberOfProjects) {
            layerSizes.addLast(middle)
            return true
        } else {
            return false
        }
    }

    def static isOdd(value) {
        return value % 2 == 1
    }

    def static isEven(value) {
        return value % 2 == 0
    }

    def static splitProjectsInLayers(List<Integer> layerSizes) {
        if (layerSizes.isEmpty()) {
            return []
        }
        int currentProjectNumber = 1
        (1..layerSizes.size()).collect {
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
    DependencyInfo createDependencies() {
        DependencyInfo info = new DependencyInfo()
        info.layerSizes = calculateLayerSizes()
        def numLayersToDependOn = Math.max(info.layerSizes.size() / 3 as int, 1)
        def projectsInLayers = splitProjectsInLayers(info.layerSizes)

        info.dependencies = [:]
        projectsInLayers.eachWithIndex { projectsForLayer, layerIndex ->
            projectsForLayer.each { projectNumber ->
                def resolvedDependencies = []
                def startingLayer = Math.max(layerIndex - numLayersToDependOn, 0)
                if(startingLayer < layerIndex) {
                    def possibleDependencies = (startingLayer..<layerIndex).collect {
                        projectsInLayers[it]
                    }.flatten()
                    // use Random with projectNumber as seed, so that we get same results each time
                    Collections.shuffle(possibleDependencies, new Random(projectNumber as long))
                    resolvedDependencies = possibleDependencies.take(maxProjectDependencies).sort()
                }
                info.dependencies.put(projectNumber, resolvedDependencies as Collection<Integer>)
            }
        }
        info
    }

    class DependencyInfo {
        List<Integer> layerSizes
        Map<Integer, Collection<Integer>> dependencies
    }
}
