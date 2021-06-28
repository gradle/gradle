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

package org.gradle.api.tasks.bundling;

import groovy.lang.Closure;
import org.gradle.work.DisableCachingByDefault;

/**
 * Assembles a JAR archive.
 */
@DisableCachingByDefault(because = "Not worth caching")
public class Jar extends org.gradle.jvm.tasks.Jar {
    @Override
    public Jar manifest(Closure<?> configureClosure) {
        super.manifest(configureClosure);
        return this;
    }

}
