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

package org.gradle.play.platform;

import org.gradle.api.Incubating;
import org.gradle.jvm.platform.JavaPlatform;
import org.gradle.language.scala.ScalaPlatform;
import org.gradle.platform.base.Platform;

/**
 * Defines and configures a Play Framework environment including versions of Play, Scala and Java.
 */
@Incubating
public interface PlayPlatform extends Platform {

    /**
     * Version of Play Framework to use
     * @return version of the Play Framework
     */
    String getPlayVersion();

    /**
     * Version of Scala Runtime to use.
     * @return version of the Scala runtime
     */
    ScalaPlatform getScalaPlatform();

    /**
     * Version of Java Runtime to use.
     * @return version of the Java runtime
     */
    JavaPlatform getJavaPlatform();
}
