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
package org.gradle.gradleplugin.foundation.favorites;

/**
 * This holds onto a favorite task. Note: a user may define a favorite task, but it may or may be not present (or may be hidden because of a temporary error in gradle files). So we hold onto the full
 * name of the task so we can get to the task should it become available.
 */
public class FavoriteTask {
    private String fullCommandLine;
    private String displayName;
    private boolean alwaysShowOutput;

    public FavoriteTask(String fullCommandLine, String displayName, boolean alwaysShowOutput) {
        this.fullCommandLine = fullCommandLine;
        this.displayName = displayName;
        this.alwaysShowOutput = alwaysShowOutput;
    }

    public String toString() {
        return displayName;
    }

    public String getFullCommandLine() {
        return fullCommandLine;
    }

    //if you're wanting to set this, go through the FavoritesEditor.
    /*package*/ void setFullCommandLine(String fullCommandLine) {
        this.fullCommandLine = fullCommandLine;
    }

    public String getDisplayName() {
        return displayName;
    }

    //if you're wanting to set this, go through the FavoritesEditor.
    /*package*/ void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public boolean alwaysShowOutput() {
        return alwaysShowOutput;
    }

    //if you're wanting to set this, go through the FavoritesEditor.
    /*package*/ void setAlwaysShowOutput(boolean alwaysShowOutput) {
        this.alwaysShowOutput = alwaysShowOutput;
    }
}
