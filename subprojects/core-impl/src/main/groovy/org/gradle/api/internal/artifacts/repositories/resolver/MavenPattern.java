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

package org.gradle.api.internal.artifacts.repositories.resolver;

public class MavenPattern {
    public static final String M2_PER_MODULE_VERSION_PATTERN = "[artifact]-[revision](-[classifier]).[ext]";
    public static final String M2_PER_MODULE_PATTERN = "[revision]/" + M2_PER_MODULE_VERSION_PATTERN;
    public static final String M2_PATTERN = "[organisation]/[module]/" + M2_PER_MODULE_PATTERN;
}
