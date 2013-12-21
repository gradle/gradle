/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.openapi.external.foundation;

/**
 * This represents an execution or refresh request sent to Gradle. Execution requests are
 * just Gradle commands (what would be the command line arguments). A refresh request is
 * what updates the task tree.
 * 
 * This is a mirror of Request inside Gradle, but this is meant to aid backward and forward compatibility by shielding you
 * from direct changes within gradle.
 * @deprecated No replacement
 */
@Deprecated
public interface RequestVersion1 {

    /**
     * @return the full gradle command line of this request
     */
    public String getFullCommandLine();

    /**
     * @return the display name of this request. Often this is the same as the full
     * command line, but favorites may specify something more user-friendly.
     */
    public String getDisplayName();

    /**
     * @return whether or not output should always be shown. If false, only show it when
     * errors occur.
     */
    public boolean forceOutputToBeShown();

    /**
    * Cancels this request.
    */
    public boolean cancel();

    public static final String EXECUTION_TYPE = "execution";
    public static final String REFRESH_TYPE = "refresh";
    public static final String UNKNOWN_TYPE_PREFIX = "[unknown]:";

    /**
     * @return the type of the request. Either EXECUTION, REFRESH, or something that
     * starts with UNKNOWN_TYPE_PREFIX (followed by an internal identifier) if its not
     * listed above.
     */
    public String getType();
}
