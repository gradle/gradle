/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.util.internal;

/**
 * by Szczepan Faber, created at: 1/16/12
 */
public interface JavaExecutableFinder {

    /**
     * Finds an executable that is part of a JDK installation based on
     * the given java home file.
     *
     * <p>You typically find them in <code>JAVA_HOME/bin</code> if
     * <code>JAVA_HOME</code> points to your JDK installation.</p>
     *
     * @param command the java executable to find.
     * @return the path to the command.
     */
    String getJdkExecutable(String command);
}
