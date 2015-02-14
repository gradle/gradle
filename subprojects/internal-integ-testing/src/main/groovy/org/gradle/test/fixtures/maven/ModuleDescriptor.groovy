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

package org.gradle.test.fixtures.maven

class ModuleDescriptor {
    String organisation
    String moduleName
    String revision

    ModuleDescriptor(String organisation, String moduleName, String revision) {
        this.organisation = organisation
        this.moduleName = moduleName
        this.revision = revision
    }

    public boolean isSnapshot(){
        revision.toUpperCase().contains('SNAPSHOT')
    }

    public String rootDirectory() {
        "${replaceDots(organisation)}/$moduleName"
    }

    public String artifactDirectory() {
        "${replaceDots(organisation)}/$moduleName/$revision"
    }

    public String artifactName(String type) {
        "$moduleName-$revision$type"
    }

    private String replaceDots(String path, String with = '/') {
        path.replaceAll('\\.', with)
    }
}
