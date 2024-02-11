/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.publish.maven.internal.publication;

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.publish.maven.MavenPomLicense;

import javax.inject.Inject;

public class DefaultMavenPomLicense implements MavenPomLicense {

    private final Property<String> name;
    private final Property<String> url;
    private final Property<String> distribution;
    private final Property<String> comments;

    @Inject
    public DefaultMavenPomLicense(ObjectFactory objectFactory) {
        name = objectFactory.property(String.class);
        url = objectFactory.property(String.class);
        distribution = objectFactory.property(String.class);
        comments = objectFactory.property(String.class);
    }

    @Override
    public Property<String> getName() {
        return name;
    }

    @Override
    public Property<String> getUrl() {
        return url;
    }

    @Override
    public Property<String> getDistribution() {
        return distribution;
    }

    @Override
    public Property<String> getComments() {
        return comments;
    }

}
