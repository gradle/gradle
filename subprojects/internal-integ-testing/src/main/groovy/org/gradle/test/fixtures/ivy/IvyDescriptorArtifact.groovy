/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.test.fixtures.ivy

class IvyDescriptorArtifact {
    String name
    String type
    String ext
    List<String> conf
    Map<String, String> mavenAttributes

    void hasAttributes(def ext, def type, def conf, def classifier = null) {
        assert this.ext == ext
        assert this.type == type
        assert this.conf == conf
        assert this.classifier == classifier
    }

    IvyDescriptorArtifact hasConf(def conf) {
        assert this.conf == conf
        return this
    }

    IvyDescriptorArtifact hasType(def type) {
        assert this.type == type
        return this
    }

    String getClassifier() {
        this.mavenAttributes.get("classifier")
    }
}
