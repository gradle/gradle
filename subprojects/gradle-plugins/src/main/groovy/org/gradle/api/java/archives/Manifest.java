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
package org.gradle.api.java.archives;

import groovy.lang.Closure;

import java.io.Writer;
import java.util.Map;

public interface Manifest {
    Attributes getAttributes();

    Map<String, Attributes> getSections();

    Manifest attributes(Map<String, ? extends Object> attributes);

    Manifest attributes(Map<String, ? extends Object> attributes, String sectionName);

    Manifest getEffectiveManifest();

    Manifest writeTo(Writer writer);

    Manifest writeTo(Object path);

    Manifest from(Object mergePath);

    Manifest from(Object mergePath, Closure closure);
}