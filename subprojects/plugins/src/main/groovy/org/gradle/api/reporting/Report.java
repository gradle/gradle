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

package org.gradle.api.reporting;

import org.gradle.api.Namer;
import org.gradle.util.Configurable;

import java.io.File;
import java.io.Serializable;

public interface Report extends Serializable, Configurable<Report> {
    
    Namer<Report> NAMER = new Namer<Report>() {
        public String determineName(Report report) {
            return report.getName();
        }
    };

    String getName();
    
    boolean isEnabled();
    
    void setEnabled(boolean enabled);

    File getDestination();

    /**
     * The type of output the report produces
     */
    enum OutputType {

        /**
         * The report outputs a single file.
         *
         * That is, the {@link #getDestination()} file points a single file.
         */
        FILE,

        /**
         * The report outputs files into a directory.
         *
         * That is, the {@link #getDestination()} file points to a directory.
         */
        DIRECTORY
    }

    OutputType getOutputType();

}
