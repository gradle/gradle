/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.tooling.model.idea;

import org.gradle.tooling.model.Element;
import org.gradle.tooling.model.HierarchicalElement;

/**
 * Represents the information about the IntelliJ IDEA project
 */
public interface IdeaProject extends HierarchicalElement, Element {

    /**
     * The name of the jdk
     *
     * @return jdk name
     */
    String getJdkName();

    /**
     * Language level to use within the current project.
     *
     * TODO SF - we need to decide how to model it. I have a POC implementation of modelling it as an enum (with graceful degradation)
     * but I'm not sure if it's worth the hassle.
     *
     * @return language level
     */
    String getLanguageLevel();

}