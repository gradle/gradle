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
package org.gradle.openapi.external.foundation.favorites;

/**
 * This is an abstraction from Gradle that allows you to work with a 'favorite' task.
 *
 * This is a mirror of FavoriteTask inside Gradle, but this is meant
 * to aid backward and forward compatibility by shielding you from direct
 * changes within gradle.
 *
 * You should not implement this yourself. Only use an implementation coming from Gradle.
 * @deprecated No replacement
 */
@Deprecated
public interface FavoriteTaskVersion1 {
    /**<!====== getFullCommandLine ============================================>
       @return the command line that is executed
    <!=======================================================================>*/
    public String getFullCommandLine();

    /**<!====== getDisplayName ================================================>
       @return a display name for this command
    <!=======================================================================>*/
    public String getDisplayName();

    /**<!====== alwaysShowOutput ==============================================>
       @return true if executing this command should always show the output, false
               to only show output if an error occurs.
    <!=======================================================================>*/
    public boolean alwaysShowOutput();
}
