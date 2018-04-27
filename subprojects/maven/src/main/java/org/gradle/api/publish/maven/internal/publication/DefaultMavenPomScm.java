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

public class DefaultMavenPomScm implements MavenPomScmInternal {

    private String connection;
    private String developerConnection;
    private String url;
    private String tag;

    @Override
    public String getConnection() {
        return connection;
    }

    @Override
    public void setConnection(String connection) {
        this.connection = connection;
    }

    @Override
    public String getDeveloperConnection() {
        return developerConnection;
    }

    @Override
    public void setDeveloperConnection(String developerConnection) {
        this.developerConnection = developerConnection;
    }

    @Override
    public String getUrl() {
        return url;
    }

    @Override
    public void setUrl(String url) {
        this.url = url;
    }

    @Override
    public String getTag() {
        return tag;
    }

    @Override
    public void setTag(String tag) {
        this.tag = tag;
    }
}
