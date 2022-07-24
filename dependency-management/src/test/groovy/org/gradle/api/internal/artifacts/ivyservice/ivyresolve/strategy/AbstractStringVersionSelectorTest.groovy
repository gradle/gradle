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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy

import org.gradle.api.artifacts.ComponentMetadata
import spock.lang.Specification

abstract class AbstractStringVersionSelectorTest extends Specification {
    abstract VersionSelector getSelector(String selector);

    def accept(String s1, String s2) {
        return getSelector(s1).accept(s2)
    }

    def accept(String s1, ComponentMetadata cmd) {
        return getSelector(s1).accept(cmd)
    }

    def isDynamic(String s1) {
        return getSelector(s1).isDynamic()
    }

    def requiresMetadata(String s1) {
        return getSelector(s1).requiresMetadata()
    }
}
