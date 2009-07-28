/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.groovy.scripts;

/**
 * Compiles a script into an executable {@code Script} object.
 */
public interface ScriptProcessor {
    /**
     * Sets the parent classloader for the script. Can be null.
     */
    ScriptProcessor setClassloader(ClassLoader classloader);

    /**
     * Sets the transformer to use to compile the script. Can be null.
     */
    ScriptProcessor setTransformer(Transformer transformer);

    /**
     * Compiles the script into a {@code Script} object of the given type.
     */
    <T extends ScriptWithSource> T process(Class<T> scriptType);
}
