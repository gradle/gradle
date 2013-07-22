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
package org.gradle.openapi.wrappers.foundation.favorites;

import org.gradle.gradleplugin.foundation.favorites.FavoriteTask;
import org.gradle.openapi.external.foundation.favorites.FavoriteTaskVersion1;

/**
 * Implementation of FavoriteTaskVersion1 meant to help shield external users from internal changes.
 */
public class FavoriteTaskWrapper implements FavoriteTaskVersion1 {

    private FavoriteTask favoriteTask;

    public FavoriteTaskWrapper(FavoriteTask favoriteTask) {
        this.favoriteTask = favoriteTask;
    }

    @Override
    public boolean equals(Object otherObject) {
        if (!(otherObject instanceof FavoriteTaskWrapper)) {
            return false;
        }

        FavoriteTaskWrapper otherFavoritesTask = (FavoriteTaskWrapper) otherObject;
        return otherFavoritesTask.favoriteTask.equals(favoriteTask);
    }

    public String getFullCommandLine() {
        return favoriteTask.getFullCommandLine();
    }

    public String getDisplayName() {
        return favoriteTask.getDisplayName();
    }

    public boolean alwaysShowOutput() {
        return favoriteTask.alwaysShowOutput();
    }

    //Only to be used internally to get the favorite task this represents
    public FavoriteTask getFavoriteTask() {
        return favoriteTask;
    }

    @Override
    public int hashCode() {
        return favoriteTask.hashCode();
    }
}
