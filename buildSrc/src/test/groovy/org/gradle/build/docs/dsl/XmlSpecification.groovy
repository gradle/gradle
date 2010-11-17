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
package org.gradle.build.docs.dsl

import spock.lang.Specification
import groovy.xml.dom.DOMUtil
import org.w3c.dom.Document
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Node

class XmlSpecification extends Specification {
    final Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()

    def format(Iterable<? extends Node> nodes) {
        document.appendChild(document.createElement('root'))
        nodes.each { node ->
            document.documentElement.appendChild(node)
        }
        return DOMUtil.serialize(document.documentElement).replaceAll(System.getProperty('line.separator'), '\n')
    }

}
