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
import org.gradle.api.provider.SetProperty;
import org.gradle.api.publish.maven.MavenPomMailingList;

public class DefaultMavenPomMailingList implements MavenPomMailingList {

    private final Property<String> name;
    private final Property<String> subscribe;
    private final Property<String> unsubscribe;
    private final Property<String> post;
    private final Property<String> archive;
    private final SetProperty<String> otherArchives;

    public DefaultMavenPomMailingList(ObjectFactory objectFactory) {
        name = objectFactory.property(String.class);
        subscribe = objectFactory.property(String.class);
        unsubscribe = objectFactory.property(String.class);
        post = objectFactory.property(String.class);
        archive = objectFactory.property(String.class);
        otherArchives = objectFactory.setProperty(String.class);
    }

    @Override
    public Property<String> getName() {
        return name;
    }

    @Override
    public Property<String> getSubscribe() {
        return subscribe;
    }

    @Override
    public Property<String> getUnsubscribe() {
        return unsubscribe;
    }

    @Override
    public Property<String> getPost() {
        return post;
    }

    @Override
    public Property<String> getArchive() {
        return archive;
    }

    @Override
    public SetProperty<String> getOtherArchives() {
        return otherArchives;
    }

}
