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

package org.gradle.api.internal.tasks.scala;

import org.gradle.api.tasks.scala.ScalaCompileOptions;

/**
 * Our internal version of the ScalaCompileOptions to hide some methods
 * which expose internal data without deprecation warnings.
 */
public class ScalaCompileOptionsInternal extends ScalaCompileOptions {

    public boolean internalIsUseAnt() {
        return useAnt;
    }

    public boolean internalIsFork() {
        return fork;
    }

    public boolean internalUseCompileDaemon() {
        return useCompileDaemon;
    }

    public String internalGetDaemonServer() {
        return daemonServer;
    }
}
