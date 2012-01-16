/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.tooling.model.build;

import java.io.File;
import java.util.List;

/**
 * Informs about the java environment, for example the java home or the jvm args used.
 * <p>
 * See example in {@link BuildEnvironment}
 *
 * @since 1.0-milestone-8
 */
public interface JavaEnvironment {

    /**
     * The java home used for gradle operations (e.g. running tasks or acquiring model information, etc).
     */
    File getJavaHome();

    /**
     * The jvm arguments used for the java process that handles gradle operations (e.g. running tasks or acquiring model information, etc).
     * <p>
     * The returned jvm arguments match those returned by {@code java.lang.management.ManagementFactory.runtimeMXBean.inputArguments},
     * e.g. they do not include system properties passed as -Dfoo=bar
     */
    List<String> getJvmArguments();
}
