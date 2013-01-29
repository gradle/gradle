/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.api.plugins.sonar.runner

//import groovy.transform.PackageScope

/**
 * An extension that is added to projects that have the
 * {@code sonar-runner} plugin applied, and all of their subprojects.
 *
 * <p>Example usage:
 *
 * <pre>
 * sonarRunner {
 *     sonarProperties {
 *         property "sonar.language", "grvy"
 *     }
 * }
 * </pre>
 */
class SonarRunnerExtension {
    /**
     * The directory where the Sonar Runner will keep files necessary for its
     * execution. This property only takes effect for projects to which the
     * {@code sonar-runner} plugin is applied (not for their subprojects).
     */
    File bootstrapDir

    /**
     * Tells if the project will be excluded from analysis.
     */
    boolean skipProject

    /**
     * Allows to configure Sonar properties. The specified code block
     * delegates to an instance of {@code SonarProperties}. Evaluation of
     * the block is deferred until the {@code sonarRunner} task executes.
     * Hence it is safe to reference any Gradle model properties
     * from the block.
     *
     * @param block configuration block for {@code SonarProperties}
     */
    void sonarProperties(Closure<?> block) {
        sonarPropertiesBlock = block
    }

    //@PackageScope
    Closure<?> sonarPropertiesBlock
}
