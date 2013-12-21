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
package org.gradle.gradleplugin.foundation;

/**
 * This allows you to add a listener that can add additional command line arguments whenever gradle is executed. This is useful if you've customized your gradle build and need to specify, for example,
 * an init script.
 */
public interface CommandLineArgumentAlteringListener {
    /**
     * This is called when you can add additional command line arguments. Return any additional arguments to add. This doesn't modify the existing commands.
     *
     * @return any command lines to add or null to leave it alone
     */
    public String getAdditionalCommandLineArguments(String commandLineArguments);
}

