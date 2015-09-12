/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.plugins.quality.internal.forking

import org.gradle.api.internal.project.AntBuilderDelegate
import org.gradle.api.internal.project.ant.AntLoggingAdapter
import org.gradle.api.internal.project.ant.BasicAntBuilder
import org.gradle.util.ConfigureUtil

class AntExecutor {

    private final BasicAntBuilder antBuilder;
    private final AntLoggingAdapter antLogger;
    private final ClassLoader classLoader;

    AntExecutor(BasicAntBuilder antBuilder, AntLoggingAdapter antLoggingAdapter, ClassLoader classLoader) {
        this.antBuilder = antBuilder;
        this.antLogger = antLoggingAdapter;
        this.classLoader = classLoader;
    }

    void execute(Closure antClosure) {
        antBuilder.project.removeBuildListener(antBuilder.project.getBuildListeners()[0])
        antBuilder.project.addBuildListener(antLogger)

        // Ideally, we'd delegate directly to the AntBuilder, but it's Closure class is different to our caller's
        // Closure class, so the AntBuilder's methodMissing() doesn't work. It just converts our Closures to String
        // because they are not an instanceof it's Closure class
        Object delegate = new AntBuilderDelegate(antBuilder, classLoader)
        ConfigureUtil.configure(antClosure, delegate)
    }
}
