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

package org.gradle.api.internal.artifacts.metadata;

import java.util.Arrays;
import java.util.Collection;

public class DefaultMavenModuleVersionMetaData implements MavenModuleVersionMetaData {
    private static final String POM_PACKAGING = "pom";
    private static final Collection<String> JAR_PACKAGINGS = Arrays.asList("ejb", "bundle", "maven-plugin", "eclipse-plugin");
    private final String packaging;
    private final boolean relocated;

    public DefaultMavenModuleVersionMetaData() {
        packaging = null;
        relocated = false;
    }

    public DefaultMavenModuleVersionMetaData(String packaging, boolean relocated) {
        this.packaging = packaging;
        this.relocated = relocated;
    }

    public String getPackaging() {
        return packaging;
    }

    public boolean isRelocated() {
        return relocated;
    }

    public boolean isPomPackaging() {
        return POM_PACKAGING.equals(packaging);
    }

    public boolean isKnownJarPackaging() {
        return packaging == null || "jar".equals(packaging) || JAR_PACKAGINGS.contains(packaging);
    }
}
