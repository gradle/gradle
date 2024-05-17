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

package org.gradle.plugins.ide.eclipse.model;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import groovy.util.Node;
import groovy.util.NodeList;
import org.gradle.internal.Cast;
import org.gradle.plugins.ide.eclipse.model.internal.PathUtil;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.toMap;

// TODO: consider entryAttributes in equals, hashCode, and toString

/**
 * Common superclass for all {@link ClasspathEntry} instances.
 */
public abstract class AbstractClasspathEntry implements ClasspathEntry {
    private static final String NATIVE_LIBRARY_ATTRIBUTE = "org.eclipse.jdt.launching.CLASSPATH_ATTR_LIBRARY_PATH_ENTRY";
    public static final String COMPONENT_NON_DEPENDENCY_ATTRIBUTE = "org.eclipse.jst.component.nondependency";
    public static final String COMPONENT_DEPENDENCY_ATTRIBUTE = "org.eclipse.jst.component.dependency";

    protected String path;
    protected boolean exported;
    protected Set<AccessRule> accessRules;
    protected final Map<String, Object> entryAttributes;

    public AbstractClasspathEntry(Node node) {
        path = normalizePath((String) node.attribute("path"));
        this.exported = isNodeExported(node);
        accessRules = readAccessRules(node);
        entryAttributes = readEntryAttributes(node);
        Preconditions.checkNotNull(path);
        Preconditions.checkNotNull(accessRules);
    }

    private boolean isNodeExported(Node node) {
        Object value = node.attribute("exported");
        if (value == null) {
            return false;
        } else if (value instanceof Boolean) {
            return (Boolean) value;
        } else {
            return Boolean.parseBoolean((String) value);
        }
    }

    public AbstractClasspathEntry(String path) {
        Preconditions.checkNotNull(path);
        this.path = normalizePath(path);
        this.exported = false;
        this.accessRules = Sets.newLinkedHashSet();
        this.entryAttributes = Maps.newLinkedHashMap();
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public boolean isExported() {
        return exported;
    }

    public void setExported(boolean exported) {
        this.exported = exported;
    }

    public Set<AccessRule> getAccessRules() {
        return accessRules;
    }

    public void setAccessRules(Set<AccessRule> accessRules) {
        this.accessRules = accessRules;
    }

    public Map<String, Object> getEntryAttributes() {
        return entryAttributes;
    }

    public String getNativeLibraryLocation() {
        return (String) entryAttributes.get(NATIVE_LIBRARY_ATTRIBUTE);
    }

    public void setNativeLibraryLocation(String location) {
        entryAttributes.put(NATIVE_LIBRARY_ATTRIBUTE, location);
    }

    @Override
    public void appendNode(Node node) {
        addClasspathEntry(node, ImmutableMap.of());
    }

    protected Node addClasspathEntry(Node node, Map<String, ?> attributes) {
        ImmutableMap.Builder<String, Object> allAttributes = ImmutableMap.builder();
        attributes.forEach((key, value) -> {
            if (value != null && !String.valueOf(value).isEmpty()) {
                allAttributes.put(key, value);
            }
        });
        allAttributes.put("kind", getKind());
        allAttributes.put("path", path);


        if (exported && !(this instanceof SourceFolder)) {
            allAttributes.put("exported", true);
        }

        Node entryNode = node.appendNode("classpathentry", allAttributes.build());
        writeAccessRules(entryNode);
        writeEntryAttributes(entryNode);
        return entryNode;
    }

    protected String normalizePath(String path) {
        return PathUtil.normalizePath(path);
    }

    private Set<AccessRule> readAccessRules(Node node) {
        Set<AccessRule> accessRules = Sets.newLinkedHashSet();
        NodeList accessRulesNodes = (NodeList) node.get("accessrules");
        for (Object accessRulesNode : accessRulesNodes) {
            NodeList accessRuleNodes = (NodeList) ((Node) accessRulesNode).get("accessrule");
            for (Object accessRuleNode : accessRuleNodes) {
                Node ruleNode = (Node) accessRuleNode;
                accessRules.add(new AccessRule((String) ruleNode.attribute("kind"), (String) ruleNode.attribute("pattern")));
            }
        }
        return accessRules;
    }

    private void writeAccessRules(Node node) {
        if (accessRules == null || accessRules.isEmpty()) {
            return;
        }
        Node accessRulesNode = getAttributesNode(node, "accessrules");
        for (AccessRule rule : accessRules) {
            accessRulesNode.appendNode("accessrule",
                ImmutableMap.of(
                    "kind", rule.getKind(),
                    "pattern", rule.getPattern()));
        }
    }

    private Map<String, Object> readEntryAttributes(Node node) {
        Map<String, Object> attributes = Maps.newLinkedHashMap();
        NodeList attributesNodes = (NodeList) node.get("attributes");
        for (Object attributesEntry : attributesNodes) {
            NodeList attributeNodes = (NodeList) ((Node) attributesEntry).get("attribute");
            for (Object attributeEntry : attributeNodes) {
                Node attributeNode = (Node) attributeEntry;
                attributes.put((String) attributeNode.attribute("name"), attributeNode.attribute("value"));
            }
        }
        return attributes;
    }

    public void writeEntryAttributes(Node node) {
        Map<String, Object> effectiveEntryAttrs = getEffectiveEntryAttrs();

        if (effectiveEntryAttrs.isEmpty()) {
            return;
        }

        Node attributesNode = getAttributesNode(node, "attributes");

        effectiveEntryAttrs.forEach((key, value) -> {
            // If the attribute value is an Iterable, an <attribute> node is produced for each element in the Iterable.
            // This is something that is supported by the classpath entry format and it allows users to define multi-value
            // entries by putting a list as value into the 'entryAttributes' Map.
            // For exmaple: entryAttributes['add-exports'] = ['java.base/jdk.internal.access=ALL-UNNAMED', 'java.base/jdk.internal.loader=ALL-UNNAMED']
            if (value instanceof Iterable) {
                Cast.<Iterable<?>>uncheckedCast(value).forEach(valueElement ->
                    attributesNode.appendNode("attribute", ImmutableMap.of(
                        "name", key,
                        "value", valueElement)
                    )
                );
            } else {
                attributesNode.appendNode("attribute", ImmutableMap.of(
                    "name", key,
                    "value", value)
                );
            }
        });
    }

    private Map<String, Object> getEffectiveEntryAttrs() {
        return entryAttributes.entrySet().stream()
            .filter(entry -> entry.getValue() != null)
            .collect(toMap(Map.Entry::getKey, Map.Entry::getValue, (existing, replacement) -> existing, LinkedHashMap::new));
    }

    private static Node getAttributesNode(Node node, String attributes) {
        NodeList attributesNodes = (NodeList) node.get(attributes);
        if (attributesNodes.isEmpty()) {
            return node.appendNode(attributes);
        }
        return (Node) attributesNodes.get(0);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AbstractClasspathEntry that = (AbstractClasspathEntry) o;
        return exported == that.exported
            && Objects.equal(path, that.path)
            && Objects.equal(accessRules, that.accessRules)
            && Objects.equal(getNativeLibraryLocation(), that.getNativeLibraryLocation());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(path, exported, accessRules, getNativeLibraryLocation());
    }

    @Override
    public String toString() {
        return "{path='" + path + "', nativeLibraryLocation='" + getNativeLibraryLocation() + "', exported=" + exported + ", accessRules=" + accessRules + "}";
    }
}

