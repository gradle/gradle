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
 * The last layer size is adjusted so the the sum of layer sizes equals the number of projects.
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

    def createDependencies() {
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
                    Collections.shuffle(possibleDependencies, new Random(projectNumber as long))
                    resolvedDependencies = possibleDependencies.take(numberOfDependencies).sort()
                }
                allProjectDependencies.put(projectNumber, resolvedDependencies)
            }
        }
        allProjectDependencies
    }
}

//workaround for referring to task types defined in plugin scripts
project.ext.set('DependencyGenerator', DependencyGenerator)
