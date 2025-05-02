/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.execution.history.changes;

import com.google.common.collect.ImmutableList;

import static com.google.common.base.Preconditions.checkArgument;

class MessageCollectingChangeVisitor implements ChangeVisitor {
    private final ImmutableList.Builder<String> messagesBuilder = ImmutableList.builder();
    private final int maxNumMessages;
    private int count;

    MessageCollectingChangeVisitor(int maxNumMessages) {
        checkArgument(maxNumMessages > 0, "maxNumMessages must be positive");
        this.maxNumMessages = maxNumMessages;
    }

    @Override
    public boolean visitChange(Change change) {
        count++;
        if (count <= maxNumMessages) {
            messagesBuilder.add(change.getMessage());
            return true;
        } else if (count == maxNumMessages + 1) {
            messagesBuilder.add("and more...");
            return false;
        } else {
            return false;
        }
    }

    public ImmutableList<String> getMessages() {
        return messagesBuilder.build();
    }
}
