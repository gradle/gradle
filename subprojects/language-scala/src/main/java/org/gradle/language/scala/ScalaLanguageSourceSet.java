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

package org.gradle.language.scala;

import org.gradle.api.Incubating;
import org.gradle.jvm.Classpath;
import org.gradle.language.base.LanguageSourceSet;

/**
 * A set of sources passed to the Scala compiler.
 */
@Incubating
@Deprecated
public interface ScalaLanguageSourceSet extends LanguageSourceSet {
    Classpath getCompileClasspath();
}
