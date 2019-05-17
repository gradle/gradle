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

package org.gradle.api.distribution;

import org.gradle.api.Action;
import org.gradle.api.Named;
import org.gradle.api.file.CopySpec;

/**
 * A distribution allow to bundle an application or a library including dependencies,sources...
 */
public interface Distribution extends Named {

    /**
     * The name of this distribution.
     */
    @Override
    String getName();

    /**
     * The baseName of the distribution, used in naming the distribution archives.
     * <p>
     * If the {@link #getName()} of this distribution is "{@code main}" this defaults to the project's name.
     * Otherwise it is "{@code $project.name-$this.name}".
     */
    String getBaseName();

    /**
     * The baseName of the distribution.
     * <p>
     * Set to change the name of the distribution archives.
     */
    void setBaseName(String baseName);

    /**
     * The contents of the distribution.
     */
    CopySpec getContents();

    /**
     * Configures the contents of the distribution.
     * <p>
     * Can be used to configure the contents of the distribution:
     * <pre class='autoTested'>
     * apply plugin: "distribution"
     *
     * distributions {
     *     main {
     *         contents {
     *             from "src/readme"
     *         }
     *     }
     * }
     * </pre>
     * The DSL inside the {@code contents\{} } block is the same DSL used for Copy tasks.
     */
    CopySpec contents(Action<? super CopySpec> action);
}
