/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.incremental.recomp;

import java.io.File;
import java.util.Collection;
import java.util.LinkedHashSet;

public class RecompilationSpec {

    private final Collection<String> classesToCompile = new NormalizingClassNamesSet();
    private String fullRebuildCause;

    public Collection<String> getClassNames() {
        return classesToCompile;
    }

    public boolean isFullRebuildNeeded() {
        return fullRebuildCause != null;
    }

    public String getFullRebuildCause() {
        return fullRebuildCause;
    }

    public void setFullRebuildCause(String description, File file) {
        fullRebuildCause = description != null? description : "'" + file.getName() + "' was changed";
    }

    private static class NormalizingClassNamesSet extends LinkedHashSet<String> {
        @Override
        public boolean add(String className) {
            int idx = className.indexOf('$');
            if (idx>0) {
                className = className.substring(0, idx);
            }
            return super.add(className);
        }

        @Override
        public boolean addAll(Collection<? extends String> classNames) {
            boolean added = false;
            for (String cn : classNames) {
                added |= add(cn);
            }
            return added;
        }
    }
}
