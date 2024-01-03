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
import org.gradle.api.publish.maven.MavenPomScm;

import javax.inject.Inject;

public class DefaultMavenPomScm implements MavenPomScm {

    private final Property<String> connection;
    private final Property<String> developerConnection;
    private final Property<String> url;
    private final Property<String> tag;

    @Inject
    public DefaultMavenPomScm(ObjectFactory objectFactory) {
        connection = objectFactory.property(String.class);
        developerConnection = objectFactory.property(String.class);
        url = objectFactory.property(String.class);
        tag = objectFactory.property(String.class);
    }

    @Override
    public Property<String> getConnection() {
        return connection;
    }

    @Override
    public Property<String> getDeveloperConnection() {
        return developerConnection;
    }

    @Override
    public Property<String> getUrl() {
        return url;
    }

    @Override
    public Property<String> getTag() {
        return tag;
    }

}
