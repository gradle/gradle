/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.api.artifacts;

import org.gradle.api.Incubating;

import java.util.Map;

/**
 * An Ivy module descriptor that acts as an input to a component metadata rule.
 */
@Incubating
public interface IvyModuleDescriptor {
    /**
     * Returns a read-only map of the extra info declared in this descriptor.
     * The extra info is the set of all non-standard subelements of the <em>info</em> element.
     * For each such element, the returned map contains one entry. The entry's key is the name
     * (i.e. tag) of the element, including any namespace prefix. The entry's value is the contents of
     * the element.
     *
     * Example: Given an <em>info</em> element with the following non-standard subelements:
     *
     * &lt;pre&gt;
     * &lt;info&gt;
     *     &lt;ns1:expires&gt;2015-09-23&lt;/ns1:expires&gt;
     *     &lt;ns2:popularity&gt;high&lt;/ns2:popularity&gt;
     * &lt;/info&gt;
     * &lt;/pre&gt;
     *
     * Then the returned map will be {@code ["ns1:expires": "2015-09-23", "ns2:popularity": "high"]}.
     *
     * @return a read-only map of the extra info declared in this descriptor
     */
    Map<String, String> getExtraInfo();
}
