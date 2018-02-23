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

package org.gradle.internal.nativeintegration.jansi;

public class JansiLibrary {
    private final String platform;
    private final String filename;

    public JansiLibrary(String platform, String filename) {
        this.platform = platform;
        this.filename = filename;
    }

    public String getPlatform() {
        return platform;
    }

    public String getFilename() {
        return filename;
    }

    public String getPath() {
        return platform + "/" + filename;
    }

    public String getResourcePath() {
        return "/META-INF/native/" + getPath();
    }
}
