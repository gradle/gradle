/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.changedetection.state;

import org.apache.commons.lang.StringUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class OverlappingDirectoriesDetector {
    private final char fileSeparatorChar;
    TreeNode rootNode = new TreeNode("", null);
    List<TreeNode> leafs = new ArrayList<TreeNode>();

    public OverlappingDirectoriesDetector() {
        this(File.separatorChar);
    }

    public OverlappingDirectoriesDetector(char fileSeparatorChar) {
        this.fileSeparatorChar = fileSeparatorChar;
    }

    public void addPaths(Collection<String> outputPaths) {
        for (String path : outputPaths) {
            String[] pathParts = StringUtils.split(path, fileSeparatorChar);
            TreeNode current = rootNode.addChild(pathParts[0]);
            for (int i = 1; i < pathParts.length; i++) {
                current = current.addChild(pathParts[i]);
            }
            current.increaseLeafCounter(path);
            leafs.add(current);
        }
    }

    public Collection<String> resolveOverlappingPaths() {
        Set<String> overlappingPaths = new HashSet<String>();
        for (TreeNode leaf : leafs) {
            List<TreeNode> overlappingNodes = leaf.findOverlapping();
            if (overlappingNodes.size() > 0 || leaf.leafCounter > 1) {
                overlappingPaths.add(leaf.getLeafPath());
            }
            for (TreeNode overlappingNode : overlappingNodes) {
                overlappingPaths.add(overlappingNode.getLeafPath());
            }
        }
        return overlappingPaths;
    }

    private static class TreeNode {
        private final String segment;
        private final TreeNode parent;
        List<TreeNode> children = new ArrayList<TreeNode>();
        int leafCounter;
        String leafPath;

        private TreeNode(String segment, TreeNode parent) {
            this.segment = segment;
            this.parent = parent;
        }

        public TreeNode addChild(String pathPart) {
            for (TreeNode child : children) {
                if (child.segment.equals(pathPart)) {
                    return child;
                }
            }
            TreeNode newRoot = new TreeNode(pathPart, this);
            children.add(newRoot);
            return newRoot;
        }

        public void increaseLeafCounter(String leafPath) {
            leafCounter++;
            this.leafPath = leafPath;
        }

        public String getLeafPath() {
            return leafPath;
        }

        public boolean hasLeafs() {
            return leafCounter > 0;
        }

        public List<TreeNode> findOverlapping() {
            List<TreeNode> overlappingNodes = new LinkedList<TreeNode>();
            TreeNode current = parent;
            while (current != null) {
                if (current.hasLeafs()) {
                    overlappingNodes.add(current);
                }
                current = current.parent;
            }
            return overlappingNodes;
        }
    }
}
