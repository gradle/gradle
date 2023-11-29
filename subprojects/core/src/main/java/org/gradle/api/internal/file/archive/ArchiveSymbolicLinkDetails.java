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

package org.gradle.api.internal.file.archive;

import org.gradle.api.file.SymbolicLinkDetails;

class ArchiveSymbolicLinkDetails<ENTRY> implements SymbolicLinkDetails {
    private final ENTRY entry;
    private String target;
    private Boolean targetExists = null;
    private ENTRY targetEntry = null;
    private final ArchiveVisitor<ENTRY> visitor;

    ArchiveSymbolicLinkDetails(ENTRY entry, ArchiveVisitor<ENTRY> visitor) {
        this.entry = entry;
        this.visitor = visitor;
    }

    @Override
    public boolean isRelative() {
        return targetExists();
    }

    @Override
    public String getTarget() {
        if (target == null) {
            target = visitor.getSymlinkTarget(entry);
        }
        return target;
    }

    @Override
    public boolean targetExists() {
        if (targetExists == null) {
            targetEntry = visitor.getTargetEntry(entry);
            targetExists = targetEntry != null;
        }
        return targetExists;
    }

    public ENTRY getTargetEntry() {
        return targetEntry;
    }
}
