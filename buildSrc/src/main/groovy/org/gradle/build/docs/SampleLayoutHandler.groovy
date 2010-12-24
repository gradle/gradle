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
package org.gradle.build.docs

import org.w3c.dom.Element

class SampleLayoutHandler {
    private final String srcDir

    SampleLayoutHandler(String srcDir) {
        this.srcDir = srcDir
    }

    void handle(String locationText, Element parentElement, Element sampleManifestElement) {
        def doc = parentElement.ownerDocument
        Element outputTitle = doc.createElement("para")
        outputTitle.appendChild(doc.createTextNode("Build layout"))
        parentElement.appendChild(outputTitle)
        Element programListingElement = doc.createElement('programlisting')
        parentElement.appendChild(programListingElement)
        StringBuilder content = new StringBuilder()

        TreeNode tree = new TreeNode(srcDir.tokenize('/').last() + '/', '', true)
        locationText.eachLine { line ->
            def fileName = line.trim().replace('\\', '/').replaceAll(/^\//, '').replaceAll(/\/+/, '/')
            if (fileName) {
                def node = new TreeNode(fileName, fileName, true)
                def nodeElement = sampleManifestElement.ownerDocument.createElement(node.file ? 'file' : 'dir')
                nodeElement.setAttribute('path', fileName)
                sampleManifestElement.appendChild(nodeElement)
                tree.children << node
            }
        }
        tree.normalise()

        tree.writeTo(content)

        programListingElement.appendChild(doc.createTextNode(content.toString()))
    }
}

class TreeNode {
    String path
    final List<String> nameParts
    List<TreeNode> children = []
    String name
    final mustInclude

    TreeNode(String name, String path = name, boolean mustInclude = true) {
        this.name = name
        this.path = path
        this.mustInclude = mustInclude
    }

    @Override
    String toString() {
        return path
    }

    boolean isFile() {
        return !path.endsWith('/')
    }

    void normalise() {
        if (children.isEmpty()) {
            return
        }

        TreeNode currentContext = null
        int pos = 0;
        while (pos < children.size()) {
            def child = children.get(pos)
            if (child.depth == 1) {
                currentContext = child
                pos++
            } else if (currentContext == null || !child.path.startsWith(currentContext.path)) {
                // Start a new stack
                def path = child.parentPath
                currentContext = new TreeNode(path, path, false)
                children.add(pos, currentContext)
                pos++
            } else {
                // Move this child down one level
                if (!currentContext.path.endsWith('/')) {
                    throw new RuntimeException("Parent directory '$currentContext.path' should end with a slash.")
                }
                def path = child.path.substring(currentContext.path.length())
                currentContext.children.add(new TreeNode(path, path, child.mustInclude))
                children.remove(pos)
            }
        }
        children.each { child ->
            child.normalise()
        }

        // Collapse this node if it has a single child which is a directory
        if (!mustInclude && children.size() == 1 && children[0].path.endsWith('/')) {
            path = path + children[0].path
            name = name + children[0].name
            children = children[0].children
        }
    }

    def getDepth() {
        return path.tokenize('/').size()
    }

    def getParentPath() {
        return path.tokenize('/')[0] + '/'
    }

    def commonPrefix(String a, String b) {
        def partsA = a.tokenize('/')
        def partsB = b.tokenize('/')
        int i = 0
        for (; i < partsA.size() && i < partsB.size() && partsA[i] == partsB[i]; i++) {
        }
        if (i == 0) {
            return null
        }
        return partsA.subList(0, i).join('/')
    }

    void writeTo(Appendable target, int depth = 0) {
        depth.times { target.append('  ') }
        target.append(name)
        target.append('\n')
        children.each { child ->
            child.writeTo(target, depth + 1)
        }
    }
}

