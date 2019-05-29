/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.play;

import org.gradle.api.Incubating;
import org.gradle.internal.HasInternalProtocol;
import org.gradle.model.Managed;
import org.gradle.platform.base.ApplicationSpec;

/**
 * Definition of a Play Framework software component that is built by Gradle.
 */
@Managed
@Incubating
@HasInternalProtocol
@Deprecated
public interface PlayApplicationSpec extends ApplicationSpec, PlayPlatformAwareComponentSpec {

    /**
     * Specifies a {@link org.gradle.play.platform.PlayPlatform} with a given set of requirements that this
     * component should be built be for.  Can accept a map of play, scala and/or java requirements or a string
     * representation of the desired play platform.
     * <pre>
     *     model {
     *          components {
     *              play {
     *                  platform 'play-2.3.9'
     *                  platform play: '2.3.2'
     *                  platform play: '2.3.6', scala: '2.10'
     *                  platform play: '2.3.7', scala: '2.11', java: '1.8'
     *              }
     *          }
     *      }
     * </pre>
     * @param platformRequirements Map of Play requirements or the name of an Play platform.
     */
    @Override
    void platform(Object platformRequirements);

    /**
     * Configures the style of router to use with this application.
     *
     * <p>
     * By default, a static routes generator is used to generate a singleton router.  This requires that all the actions
     * that the router invokes on the application's controllers are either Scala singleton objects, or Java static methods.
     * </p>
     *
     * <p>
     * In Play 2.4+, a injected routes generator is recommended.  This requires that the router declares its
     * dependencies to the application's controllers in its constructor.  The controllers methods need to be instance methods.
     * </p>
     *
     * @param injectedRoutesGenerator false if a static router should be generated (default).
     * true if an injected router should be generated.
     */
    void setInjectedRoutesGenerator(boolean injectedRoutesGenerator);

    /**
     * Will an injected router be generated for this application?
     *
     * @return false if a static router will be generated for the application,
     * true if an injected router will be generated.
     */
    boolean getInjectedRoutesGenerator();
}
