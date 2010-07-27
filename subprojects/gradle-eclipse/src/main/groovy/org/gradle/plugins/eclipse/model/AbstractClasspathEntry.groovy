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
package org.gradle.plugins.eclipse.model

/**
 * @author Hans Dockter
 */
abstract class AbstractClasspathEntry implements ClasspathEntry {
    static final String NATIVE_LIBRARY_ATTRIBUTE = 'org.eclipse.jdt.launching.CLASSPATH_ATTR_LIBRARY_PATH_ENTRY'
    String path
    String nativeLibraryLocation
    boolean exported
    Set<AccessRule> accessRules

    def AbstractClasspathEntry(Node node) {
        this.path = node.@path
        this.exported = node.@exported
        this.nativeLibraryLocation = readNativeLibraryLocation(node)
        this.accessRules = readAccessRules(node)
        assert path != null && accessRules != null
    }

    def AbstractClasspathEntry(String path, boolean exported, String nativeLibraryLocation, Set accessRules) {
        assert path != null && accessRules != null
        this.path = path;
        this.exported = exported
        this.nativeLibraryLocation = nativeLibraryLocation
        this.accessRules = accessRules
    }

    Map removeNullEntries(Map args) {
        def result = [:]
        args.each { key, value ->
            if (value) {
                result[key] = value
            }
        }
        result
    }

    Node addClasspathEntry(Node node, Map attributes) {
        def allAttributes = removeNullEntries(attributes) + [
                kind: getKind(),
                path: path]
        allAttributes += exported && !(this instanceof SourceFolder) ? [exported: exported] : [:]
        Node entryNode = node.appendNode('classpathentry', allAttributes)
        addNativeLibraryLocation(entryNode)
        addAccessRules(entryNode)
        entryNode
    }

    void addNativeLibraryLocation(Node node) {
        addEntryAttributes(node, removeNullEntries([(NATIVE_LIBRARY_ATTRIBUTE): nativeLibraryLocation]))
    }

    String readNativeLibraryLocation(Node node) {
        node.attributes.attribute.find { it.@name == 'org.eclipse.jdt.launching.CLASSPATH_ATTR_LIBRARY_PATH_ENTRY' }?.attributes()?.value
    }

    void addAccessRules(Node node) {
        if (!accessRules) {
            return
        }
        Node accessRulesNode = null
        if (node.accessrules.size() == 0) {
            accessRulesNode = node.appendNode('accessrules')
        } else {
            accessRulesNode = node.accessrules[0]
        }
        accessRules.each { AccessRule rule ->
            accessRulesNode.appendNode('accessrule', [kind: rule.kind, pattern: rule.pattern])
        }
    }

    def readAccessRules(Node node) {
        node.accessrules.accessrule.collect { ruleNode ->
            new AccessRule(ruleNode.@kind, ruleNode.@pattern)
        }
    }

    void addEntryAttributes(Node node, Map attributes) {
        if (!attributes) {
            return
        }
        Node attributesNode = node.children().find { it.name()  == 'attributes' }
        if (!attributesNode) {
            attributesNode = node.appendNode('attributes')
        }
        attributes.each { key, value ->
            attributesNode.appendNode('attribute', [name: key, value: value])
        }
    }

    boolean equals(o) {
        if (this.is(o)) { return true }

        if (getClass() != o.class) { return false }

        AbstractClasspathEntry that = (AbstractClasspathEntry) o;

        if (exported != that.exported) { return false }
        if (accessRules != that.accessRules) { return false }
        if (nativeLibraryLocation != that.nativeLibraryLocation) { return false }
        if (path != that.path) { return false }

        return true
    }

    int hashCode() {
        int result;

        result = path.hashCode();
        result = 31 * result + (nativeLibraryLocation != null ? nativeLibraryLocation.hashCode() : 0);
        result = 31 * result + (exported ? 1 : 0);
        result = 31 * result + accessRules.hashCode();
        return result;
    }

    public String toString() {
        return "{" +
                "path='" + path + '\'' +
                ", nativeLibraryLocation='" + nativeLibraryLocation + '\'' +
                ", exported=" + exported +
                ", accessRules=" + accessRules +
                '}';
    }
}
