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

package org.gradle.api.internal.changedetection.rules;

import java.util.ArrayList;
import java.util.List;

// TODO:DAZ Unit Test
abstract class SimpleTaskStateChanges implements TaskStateChanges {
    private List<TaskStateChange> changes;

    public void findChanges(UpToDateChangeListener listener) {
        if (changes == null) {
            changes = new ArrayList<TaskStateChange>();
            addAllChanges(changes);
        }

        for (TaskStateChange change : changes) {
            if (!listener.isAccepting()) {
                break;
            }
            listener.accept(change);
        }
    }

    protected abstract void addAllChanges(List<TaskStateChange> changes);

    public void snapshotAfterTask() {
    }
}
