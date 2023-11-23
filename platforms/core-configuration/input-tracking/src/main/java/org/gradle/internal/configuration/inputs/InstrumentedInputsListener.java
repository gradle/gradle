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

package org.gradle.internal.configuration.inputs;

import javax.annotation.Nullable;
import java.io.File;

public interface InstrumentedInputsListener {
    /**
     * Invoked when the code reads the system property with the String key.
     *
     * @param key the name of the property
     * @param value the value of the property at the time of reading or {@code null} if the property is not present
     * @param consumer the name of the class that is reading the property value
     */
    void systemPropertyQueried(String key, @Nullable Object value, String consumer);

    /**
     * Invoked when the code updates or adds the system property.
     *
     * @param key the name of the property, can be non-string
     * @param value the new value of the property, can be {@code null} or non-string
     * @param consumer the name of the class that is updating the property value
     */
    void systemPropertyChanged(Object key, @Nullable Object value, String consumer);

    /**
     * Invoked when the code removes the system property. The property may not be present.
     *
     * @param key the name of the property, can be non-string
     * @param consumer the name of the class that is removing the property value
     */
    void systemPropertyRemoved(Object key, String consumer);

    /**
     * Invoked when all system properties are removed.
     *
     * @param consumer the name of the class that is removing the system properties
     */
    void systemPropertiesCleared(String consumer);

    /**
     * Invoked when the code reads the environment variable.
     *
     * @param key the name of the variable
     * @param value the value of the variable
     * @param consumer the name of the class that is reading the variable
     */
    void envVariableQueried(String key, @Nullable String value, String consumer);

    /**
     * Invoked when the code starts an external process. The command string with all argument is provided for reporting but its value may not be suitable to actually invoke the command because all
     * arguments are joined together (separated by space) and there is no escaping of special characters.
     *
     * @param command the command used to start the process (with arguments)
     * @param consumer the name of the class that is starting the process
     */
    void externalProcessStarted(String command, String consumer);

    /**
     * Invoked when the code opens a file.
     *
     * @param file the absolute file that was open
     * @param consumer the name of the class that is opening the file
     */
    void fileOpened(File file, String consumer);

    void fileObserved(File file, String consumer);

    void fileSystemEntryObserved(File file, String consumer);

    void directoryContentObserved(File file, String consumer);
}
