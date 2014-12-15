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

package org.gradle.play.internal.spec;

import org.gradle.api.tasks.compile.BaseForkOptions;
import org.gradle.language.base.internal.compile.CompileSpec;
import org.gradle.scala.internal.reflect.ScalaMethod;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

/**
 * A spec that provides version depending compiler metadata.
 */
public interface VersionedPlayCompileSpec extends CompileSpec {
    Object getDependencyNotation();

    ScalaMethod getCompileMethod(ClassLoader cl) throws ClassNotFoundException;

    Object[] createCompileParameters(ClassLoader cl, File file) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException;

    List<String> getClassLoaderPackages();

    BaseForkOptions getForkOptions();
}
