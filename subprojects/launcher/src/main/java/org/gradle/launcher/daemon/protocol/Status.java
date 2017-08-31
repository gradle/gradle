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

package org.gradle.launcher.daemon.protocol;

import javax.annotation.Nullable;
import java.io.Serializable;

public class Status implements Serializable {
    @Nullable
    private final Long pid;
    private final String version;
    private final String status;

    public Status(Long pid, String version, String status) {
        this.pid = pid;
        this.version = version;
        this.status = status;
    }

    @Nullable
    public Long getPid() {
        return pid;
    }

    public String getVersion() {
        return version;
    }

    public String getStatus() {
        return status;
    }
}
