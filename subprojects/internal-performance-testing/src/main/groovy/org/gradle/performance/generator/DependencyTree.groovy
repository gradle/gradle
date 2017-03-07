/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.performance.generator

import org.gradle.internal.Pair

class DependencyTree {

    private List<Pair<Integer, Integer>> nodeSets = []

    private List<List<Integer>> levelToNodes = []
    private Map<Integer, List<Integer>> parentToChildrenNodes = new HashMap<>()

    private List<List<Integer>> levelToNodeSets = []
    private Map<Integer, List<Integer>> parentToChildrenNodeSets = new HashMap<>()

    private Set<Integer> transitiveNodes = new HashSet<>()

    Map<Integer, List<Integer>> getParentToChildrenNodes() {
        return parentToChildrenNodes
    }

    Map<Integer, List<Integer>> getParentToChildrenNodeSets() {
        return parentToChildrenNodeSets
    }

    DependencyTree calcNodeDependencies(int firstNodeInSet, int lastNodeInSet, int groupSize) {
        nodeSets.add(Pair.of(firstNodeInSet, lastNodeInSet))
        (firstNodeInSet..lastNodeInSet).each {
            calcLevel(it, 0, firstNodeInSet, lastNodeInSet, groupSize, levelToNodes, parentToChildrenNodes)
        }
        this
    }

    DependencyTree calcNodeSetDependencies(int groupSize) {
        (0..nodeSets.size() - 1).each {
            calcLevel(it, 0, 0, Integer.MAX_VALUE, groupSize, levelToNodeSets, parentToChildrenNodeSets)
        }
        def dependencyLevel = 2
        //for each class on the consumer side, add a dependency on the provider side
        for (int parentNumber = 0; parentNumber < nodeSets.size(); parentNumber++) {
            def firstNodeSet = true
            for (int childNumber : parentToChildrenNodeSets.get(parentNumber)) {
                Pair<Integer, Integer> parentNodeSet = nodeSets.get(parentNumber)
                Pair<Integer, Integer> childNodeSet = nodeSets.get(childNumber)
                def parentNodes = []
                for (Integer parentNode : levelToNodes.get(dependencyLevel)) {
                    if (isInNodeSet(parentNode, parentNodeSet.left, parentNodeSet.right)) {
                        parentNodes.add(parentNode)
                    }
                }
                int idx = 0
                for (Integer childNode : levelToNodes.get(dependencyLevel)) {
                    if (isInNodeSet(childNode, childNodeSet.left, childNodeSet.right)) {
                        parentToChildrenNodes.get(parentNodes.get(idx)).add(childNode)
                        if (firstNodeSet) {
                            transitiveNodes.add(childNode)
                        }
                        idx++
                    }
                }
                firstNodeSet = false
            }
        }
        this
    }

    private isInNodeSet(int node, int firstNodeInSet, int lastNodeInSet) {
        firstNodeInSet <= node && node <= lastNodeInSet
    }

    private calcLevel(int node, int level, int firstNodeInSet, int lastNodeInSet, nodesPerGroup, List<List<Integer>> levelList, Map<Integer, List<Integer>> parentMap) {
        if (freeSpotAbove(level, firstNodeInSet, lastNodeInSet, nodesPerGroup, levelList)) {
            //a free spot on one of the level above
            calcLevel(node, level + 1, firstNodeInSet, lastNodeInSet, nodesPerGroup, levelList, parentMap)
        } else {
            addToLevel(levelList, level, node)
            List<Integer> lastGroupOnLevelBelow
            if (level > 0) {
                def nodesOnLevelBelow = getFromLevel(levelList, firstNodeInSet, lastNodeInSet, level - 1)
                lastGroupOnLevelBelow = new ArrayList<>(nodesOnLevelBelow.subList(nodesOnLevelBelow.size() - nodesPerGroup, nodesOnLevelBelow.size()))
            } else {
                lastGroupOnLevelBelow = []
            }
            parentMap.put(node, lastGroupOnLevelBelow)
        }
    }

    private freeSpotAbove(int level, int firstNodeInSet, int lastNodeInSet, nodesPerGroup, List<List<Integer>> levelList) {
        int nodesOnCurrentLevel = getFromLevel(levelList, firstNodeInSet, lastNodeInSet, level).size()
        int fullGroupsOnCurrentLevel = (int) (nodesOnCurrentLevel / nodesPerGroup)
        int nodesOnLevelAbove = getFromLevel(levelList, firstNodeInSet, lastNodeInSet, level + 1).size()
        if (nodesOnCurrentLevel == 0 && nodesOnLevelAbove == 0) {
            return false
        }
        nodesOnLevelAbove < fullGroupsOnCurrentLevel || freeSpotAbove(level + 1, firstNodeInSet, lastNodeInSet, nodesPerGroup, levelList)
    }

    private getFromLevel(List<List<Integer>> list, int firstNodeInSet, int lastNodeInSet, int level) {
        if (list.size() <= level) {
            return []
        }
        filterBySet(list.get(level), firstNodeInSet, lastNodeInSet)
    }

    private addToLevel(List<List<Integer>> list, int level, int node) {
        if (list.size() == level) {
            list.add([])
        }
        list.get(level).add(node)
    }

    def allChildrenNodes(int node) {
        List<Integer> result = []
        result.addAll(parentToChildrenNodes.get(node))
        parentToChildrenNodes.get(node).each {
            allVisibleChildrenNodes(it, result)
        }
        return result
    }

    private allVisibleChildrenNodes(int node, List<Integer> result) {
        if (parentToChildrenNodes.containsKey(node) && !parentToChildrenNodes.get(node).isEmpty()) {
            parentToChildrenNodes.get(node).each {
                if (transitiveNodes.contains(it)) {
                    result.add(it)
                    allVisibleChildrenNodes(it, result)
                }
            }
        }
    }

    private filterBySet(List<Integer> list, int firstNodeInSet, int lastNodeInSet) {
        def filtered = []
        for (Integer node : list) {
            if (isInNodeSet(node, firstNodeInSet, lastNodeInSet)) {
                filtered.add(node)
            }
        }
        return filtered
    }

    int findNodeSet(int node) {
        for (int nodeSet = 0; nodeSet < nodeSets.size(); nodeSet++) {
            if (isInNodeSet(node, nodeSets.get(nodeSet).left, nodeSets.get(nodeSet).right)) {
                return nodeSet
            }
        }
    }

    def hasParentNodeSets(Integer nodeSet) {
        if (nodeSet == null) {
            return false
        }
        for (def childLists : parentToChildrenNodeSets.values()) {
            if (childLists.contains(nodeSet)) {
                return true
            }
        }
        false
    }

    @Override
    String toString() {
        printTree("", "")
    }

    def printTree(String prefix, String suffix) {
        def printed = '**Levels: ' + levelToNodes.size() - 1 + "**\n"
        for (int node : levelToNodes.get(levelToNodes.size() - 1)) {
            printed += printNodeWithChildren(node, '', prefix, suffix)
        }
        printed
    }

    def printNodeWithChildren(int node, String indent, String prefix, String suffix) {
        def printed = indent + "->" + prefix + node + suffix + '\n'
        for (int child : parentToChildrenNodes.get(node)) {
            printed += printNodeWithChildren(child, indent + '--', prefix, suffix)
        }
        printed
    }

    static void main(String[] args) {
        def tree = new DependencyTree()
        tree.calcNodeDependencies(0, 99, 3)
        tree.calcNodeDependencies(100, 199, 3)
        tree.calcNodeDependencies(200, 299, 3)
        tree.calcNodeDependencies(300, 399, 3)
        tree.calcNodeSetDependencies(3)

        print tree.printTree("Class", ".java")
    }
}
