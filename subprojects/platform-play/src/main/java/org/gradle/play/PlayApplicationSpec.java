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
import org.gradle.platform.base.PlatformAwareComponentSpec;

/**
 * Definition of a Play Framework software component that is built by Gradle.
 */
@Incubating @HasInternalProtocol
public interface PlayApplicationSpec extends PlatformAwareComponentSpec {

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
    void platform(Object platformRequirements);
}
