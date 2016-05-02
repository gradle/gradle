/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.api;

import groovy.util.Node;
import org.w3c.dom.Element;

/**
 * Provides various ways to access the content of an XML document.
 */
public interface XmlProvider {
    /**
     * Returns the XML document as a {@link StringBuilder}. Changes to the returned instance will be applied to the XML.
     * The returned instance is only valid until one of the other methods on this interface are called.
     *
     * @return A {@code StringBuilder} representation of the XML.
     */
    StringBuilder asString();

    /**
     * Returns the XML document as a Groovy {@link groovy.util.Node}. Changes to the returned instance will be applied
     * to the XML. The returned instance is only valid until one of the other methods on this interface are called.
     *
     * @return A {@code Node} representation of the XML.
     */
    Node asNode();

    /**
     * Returns the XML document as a DOM {@link org.w3c.dom.Element}. Changes to the returned instance will be applied
     * to the XML. The returned instance is only valid until one of the other methods on this interface are called.
     *
     * @return An {@code Element} representation of the XML.
     */
    Element asElement();
}
