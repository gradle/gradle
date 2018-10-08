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
package org.gradle.plugins.ide.internal.generator;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import groovy.lang.Closure;
import groovy.util.Node;
import groovy.util.XmlParser;
import org.gradle.api.Action;
import org.gradle.api.XmlProvider;
import org.gradle.internal.Cast;
import org.gradle.internal.xml.XmlTransformer;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.gradle.util.ConfigureUtil.configureUsing;

/**
 * A {@link org.gradle.plugins.ide.internal.generator.generator.PersistableConfigurationObject}
 * which is stored in an XML file.
 */
public abstract class XmlPersistableConfigurationObject extends AbstractPersistableConfigurationObject {

    private final XmlTransformer xmlTransformer;
    private Node xml;

    protected XmlPersistableConfigurationObject(XmlTransformer xmlTransformer) {
        this.xmlTransformer = xmlTransformer;
    }

    @Override
    public void load(InputStream inputStream) throws Exception {
        xml = new XmlParser().parse(inputStream);
        load(xml);
    }

    @Override
    public void store(OutputStream outputStream) {
        store(xml);
        xmlTransformer.transform(xml, outputStream);
    }

    public Node getXml() {
        return xml;
    }

    /**
     * Called immediately after the XML file has been read.
     */
    protected void load(Node xml) {
        // no-op
    }

    /**
     * Called immediately before the XML file is to be written.
     */
    protected void store(Node xml) {
        // no-op
    }

    public void transformAction(Closure action) {
        transformAction(configureUsing(action));
    }

    /**
     * @param action transform action
     * @since 3.5
     */
    public void transformAction(Action<? super XmlProvider> action) {
        xmlTransformer.addAction(action);
    }

    protected static List<Node> getChildren(@Nullable Node root, String name) {
        return root == null ? Collections.<Node>emptyList() : Cast.<List<Node>>uncheckedCast(root.get(name));
    }

    @Nullable
    public static Node findFirstChildNamed(@Nullable Node root, String name) {
        return root == null ? null : Iterables.getFirst(getChildren(root, name), null);
    }

    @Nullable
    public static Node findFirstChildWithAttributeValue(@Nullable Node root, String childName, String attribute, String value) {
        return root == null ? null : findFirstWithAttributeValue(getChildren(root, childName), attribute, value);
    }

    @Nullable
    protected static Node findFirstWithAttributeValue(@Nullable List<Node> nodes, final String attribute, final String value) {
        return nodes == null ? null : Iterables.getFirst(Iterables.filter(nodes, new Predicate<Node>() {
            @Override
            public boolean apply(Node node) {
                return value.equals(node.attribute(attribute));
            }
        }), null);
    }

    public static Node findOrCreateFirstChildNamed(Node root, String name) {
        Node child = findFirstChildNamed(root, name);
        if (child == null) {
            child = root.appendNode(name);
        }
        return child;
    }

    public static Node findOrCreateFirstChildWithAttributeValue(@Nullable Node root, String childName, String attribute, String value) {
        Node child = findFirstChildWithAttributeValue(root, childName, attribute, value);
        if (child == null) {
            Map<String, Object> attributes = Maps.newLinkedHashMap();
            attributes.put(attribute, value);
            child = root.appendNode(childName, attributes);
        }
        return child;
    }
}
