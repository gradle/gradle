/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.tasks.cache.origin;

import org.gradle.api.internal.tasks.cache.OriginMetadata;

public class DefaultOriginMetadata implements OriginMetadata {
    private final String path;
    private final String type;
    private final String gradleVersion;
    private final long creationTime;
    private final long executionTime;

    private final String rootPath;
    private final String operatingSystem;
    private final String hostName;
    private final String userName;

    public DefaultOriginMetadata(String path, String type, String gradleVersion, long creationTime, long executionTime, String rootPath, String operatingSystem, String hostName, String userName) {
        this.path = path;
        this.type = type;
        this.gradleVersion = gradleVersion;
        this.creationTime = creationTime;
        this.executionTime = executionTime;
        this.rootPath = rootPath;
        this.operatingSystem = operatingSystem;
        this.hostName = hostName;
        this.userName = userName;
    }

    @Override
    public String toString() {
        return "origin{"
            + "path='" + path + '\''
            + ", type='" + type + '\''
            + ", gradleVersion='" + gradleVersion + '\''
            + ", creationTime=" + creationTime
            + ", executionTime=" + executionTime
            + ", rootPath='" + rootPath + '\''
            + ", operatingSystem='" + operatingSystem + '\''
            + ", hostName='" + hostName + '\''
            + ", userName='" + userName + '\'' + '}';
    }

    @Override
    public long getCreationTime() {
        return creationTime;
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public String getGradleVersion() {
        return gradleVersion;
    }

    @Override
    public long getExecutionTime() {
        return executionTime;
    }

    @Override
    public String getRootPath() {
        return rootPath;
    }

    @Override
    public String getOperatingSystem() {
        return operatingSystem;
    }

    @Override
    public String getHostName() {
        return hostName;
    }

    @Override
    public String getUserName() {
        return userName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DefaultOriginMetadata that = (DefaultOriginMetadata) o;

        if (creationTime != that.creationTime) {
            return false;
        }
        if (executionTime != that.executionTime) {
            return false;
        }
        if (!path.equals(that.path)) {
            return false;
        }
        if (!type.equals(that.type)) {
            return false;
        }
        if (!gradleVersion.equals(that.gradleVersion)) {
            return false;
        }
        if (!rootPath.equals(that.rootPath)) {
            return false;
        }
        if (!operatingSystem.equals(that.operatingSystem)) {
            return false;
        }
        if (!hostName.equals(that.hostName)) {
            return false;
        }
        return userName.equals(that.userName);
    }

    @Override
    public int hashCode() {
        int result = path.hashCode();
        result = 31 * result + type.hashCode();
        result = 31 * result + gradleVersion.hashCode();
        result = 31 * result + (int) (creationTime ^ (creationTime >>> 32));
        result = 31 * result + (int) (executionTime ^ (executionTime >>> 32));
        result = 31 * result + rootPath.hashCode();
        result = 31 * result + operatingSystem.hashCode();
        result = 31 * result + hostName.hashCode();
        result = 31 * result + userName.hashCode();
        return result;
    }
}
