/*
 * Copyright 2023 the original author or authors.
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

     package org.gradle.tooling.events.problems;

     import org.gradle.api.Incubating;

/**
 * Represents a plugin ID.
 * TODO: Does PluginIdLocation even make sense? We will probably encode this information into the category. Maybe this is something that we can implicitly map on the client side (i.e. generating
 * a PluginId location in the client based on the category).
 * @since 8.6
 */
@Incubating
public interface PluginIdLocation extends Location {
    String getPluginId();
}
