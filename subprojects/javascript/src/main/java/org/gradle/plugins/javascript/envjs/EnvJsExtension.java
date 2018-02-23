/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.plugins.javascript.envjs;

import org.gradle.api.file.FileCollection;

public class EnvJsExtension {

    public static final String NAME = "envJs";

    public static final String DEFAULT_DEPENDENCY_VERSION = "1.2";
    public static final String DEFAULT_DEPENDENCY_GROUP = "com.envjs";
    public static final String DEFAULT_DEPENDENCY_MODULE = "envjs.rhino";

    public static final String CONFIGURATION_NAME = "envJsPlugin";

    private String version;
    private FileCollection js;

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public FileCollection getJs() {
        return js;
    }

    public void setJs(FileCollection js) {
        this.js = js;
    }

}
