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
package org.gradle.plugins.ide.eclipse.model

import org.gradle.plugins.ide.eclipse.model.internal.PathUtil

// TODO: consider entryAttributes in equals, hashCode, and toString
abstract class AbstractClasspathEntry implements ClasspathEntry {
    private static final String NATIVE_LIBRARY_ATTRIBUTE = 'org.eclipse.jdt.launching.CLASSPATH_ATTR_LIBRARY_PATH_ENTRY'
    public static final String COMPONENT_NON_DEPENDENCY_ATTRIBUTE = 'org.eclipse.jst.component.nondependency'
    public static final String COMPONENT_DEPENDENCY_ATTRIBUTE = 'org.eclipse.jst.component.dependency'

    String path
    boolean exported
    Set<AccessRule> accessRules
    final Map<String, Object> entryAttributes

    AbstractClasspathEntry(Node node) {
        path = normalizePath(node.@path)
        exported = node.@exported
        accessRules = readAccessRules(node)
        entryAttributes = readEntryAttributes(node)
        assert path != null && accessRules != null
    }

    AbstractClasspathEntry(String path) {
        assert path != null
        this.path = normalizePath(path);
        this.exported = false
        this.accessRules = [] as Set
        entryAttributes = [:]
    }

    String getNativeLibraryLocation() {
        entryAttributes.get(NATIVE_LIBRARY_ATTRIBUTE)
    }

    final void setNativeLibraryLocation(String location) {
        entryAttributes.put(NATIVE_LIBRARY_ATTRIBUTE, location)
    }

    void appendNode(Node node) {
        addClasspathEntry(node, [:])
    }

    protected Node addClasspathEntry(Node node, Map<String, ?> attributes) {
        def allAttributes = attributes.findAll { it.value } + [kind: getKind(), path: path]
        if (exported && !(this instanceof SourceFolder)) {
            allAttributes.exported = true
        }
        Node entryNode = node.appendNode('classpathentry', allAttributes)
        writeAccessRules(entryNode)
        writeEntryAttributes(entryNode)
        entryNode
    }

    protected String normalizePath(String path) {
        PathUtil.normalizePath(path)
    }

    private Set readAccessRules(Node node) {
        node.accessrules.accessrule.collect { ruleNode ->
            new AccessRule(ruleNode.@kind, ruleNode.@pattern)
        }
    }

    private void writeAccessRules(Node node) {
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

    private Map readEntryAttributes(Node node) {
        def attrs = [:]
        node.attributes.attribute.each {
            attrs.put(it.@name, it.@value)
        }
        attrs
    }

    void writeEntryAttributes(Node node) {
        def effectiveEntryAttrs = entryAttributes.findAll { it.value || it.key == COMPONENT_NON_DEPENDENCY_ATTRIBUTE }
        if (!effectiveEntryAttrs) { return }

        if (effectiveEntryAttrs.containsKey(COMPONENT_DEPENDENCY_ATTRIBUTE)
                && effectiveEntryAttrs.containsKey(COMPONENT_NON_DEPENDENCY_ATTRIBUTE)) {
            //For conflicting component dependency entries, the non-dependency loses
            //because it is our default and it means the user has configured something else.
            effectiveEntryAttrs.remove(COMPONENT_NON_DEPENDENCY_ATTRIBUTE)
        }

        Node attributesNode = node.children().find { it.name()  == 'attributes' } ?: node.appendNode('attributes')
        effectiveEntryAttrs.each { key, value ->
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
