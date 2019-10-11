/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.vfs.impl

import spock.lang.Specification

class RootNodeTest extends Specification {

    def "can add unix style children"() {
        def node = new RootNode()

        def directChild = node
            .getOrCreateChild("") { parent -> new DefaultNode("", parent) }
            .getOrCreateChild("var") { parent -> new DefaultNode("var", parent) }
        expect:
        directChild.absolutePath == "${File.separator}var"
        directChild
            .getOrCreateChild("log") { parent -> new DefaultNode("log", parent) }
            .absolutePath == ["", "var", "log"].join(File.separator)
    }

    def "can add Windows style children"() {
        def node = new RootNode()

        def directChild = node
            .getOrCreateChild("C:") { parent -> new DefaultNode("C:", parent) }
        expect:
        directChild.absolutePath == "C:"
        directChild
            .getOrCreateChild("Users") { parent -> new DefaultNode("Users", parent) }
            .absolutePath == ["C:", "Users"].join(File.separator)
    }
}
