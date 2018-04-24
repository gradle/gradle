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

import org.gradle.api.publish.maven.MavenPomMailingList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DefaultMavenPomMailingList implements MavenPomMailingList {

    private String name;
    private String subscribe;
    private String unsubscribe;
    private String post;
    private String archive;
    private final List<String> otherArchives = new ArrayList<String>();

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getSubscribe() {
        return subscribe;
    }

    @Override
    public void setSubscribe(String subscribe) {
        this.subscribe = subscribe;
    }

    @Override
    public String getUnsubscribe() {
        return unsubscribe;
    }

    @Override
    public void setUnsubscribe(String unsubscribe) {
        this.unsubscribe = unsubscribe;
    }

    @Override
    public String getPost() {
        return post;
    }

    @Override
    public void setPost(String post) {
        this.post = post;
    }

    @Override
    public void setArchive(String archive) {
        this.archive = archive;
    }

    @Override
    public String getArchive() {
        return archive;
    }

    @Override
    public void otherArchives(String... otherArchive) {
        this.otherArchives.addAll(Arrays.asList(otherArchive));
    }

    @Override
    public List<String> getOtherArchives() {
        return otherArchives;
    }

}
