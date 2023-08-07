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

package org.gradle.api.file;

import org.gradle.api.GradleException;

import javax.annotation.Nullable;
import java.io.Serializable;

public interface LinksStrategy extends Serializable {
    /**
     * Do not preserve any symlinks. This is the default.
     */
    LinksStrategy NONE = new LinksStrategy() {};
    /**
     * Preserve relative symlinks.
     */
    LinksStrategy RELATIVE = new LinksStrategy() {
        @Override
        public boolean shouldBePreserved(@Nullable SymbolicLinkDetails linkDetails, String originalPath) {
            if (linkDetails == null) {
                return false;
            }
            if (!linkDetails.isRelative()) {
                throw new GradleException(String.format("Links strategy is set to RELATIVE, but a symlink pointing outside was visited: %s pointing to %s.", originalPath, linkDetails.getTarget()));
            }
            return true;
        }
    };
    /**
     * Preserve all symlinks, even if they point to non-existent paths.
     */
    LinksStrategy ALL = new LinksStrategy() {
        @Override
        public boolean shouldBePreserved(@Nullable SymbolicLinkDetails linkDetails, String originalPath) {
            return linkDetails != null;
        }

        @Override
        public void maybeThrowOnBrokenLink(@Nullable SymbolicLinkDetails linkDetails, String originalPath) {
            // do nothing
        }
    };
    /**
     * Throw an error if a symlink is visited.
     */
    LinksStrategy ERROR = new LinksStrategy() {
        @Override
        public boolean shouldBePreserved(@Nullable SymbolicLinkDetails linkDetails, String originalPath) {
            if (linkDetails != null) {
                throw new GradleException(String.format("Links strategy is set to ERROR, but a symlink was visited: %s pointing to %s.", originalPath, linkDetails.getTarget()));
            }
            return false;
        }
    };

    default boolean shouldBePreserved(@Nullable SymbolicLinkDetails linkDetails, String originalPath) {
        return false;
    }

    default void maybeThrowOnBrokenLink(@Nullable SymbolicLinkDetails linkDetails, String originalPath) {
        if (linkDetails != null && !linkDetails.targetExists()) {
            throw new GradleException(String.format("Couldn't follow symbolic link '%s'.", originalPath));
        }
    }

}
