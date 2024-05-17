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
package org.gradle.plugins.ide.idea.model;

import groovy.util.Node;

/**
 * Represents a dependency of an IDEA module.
 */
public interface Dependency {

    /**
     * The scope of this library. If <code>null</code>, the scope attribute is not added.
     * @since 4.5
     */
    String getScope();

    /**
     * The scope of this library. If <code>null</code>, the scope attribute is not added.
     * @since 4.5
     */
    void setScope(String scope);

    void addToNode(Node parentNode);
}
