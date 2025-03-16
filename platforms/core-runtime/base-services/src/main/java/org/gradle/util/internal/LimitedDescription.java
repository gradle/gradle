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

package org.gradle.util.internal;

import com.google.common.collect.Lists;

import java.util.LinkedList;
import java.util.List;

/**
 * Discards old entries when current count is over the limit.
 *
 * @deprecated Used only by a deprecated public class GFileUtils. Prefer Guava's EvictionQueue in the new code.
 */
@Deprecated
public class LimitedDescription {

    private final LinkedList<String> content;
    private final int maxItems;

    public LimitedDescription(int maxItems) {
        this.maxItems = maxItems;
        this.content = new LinkedList<String>();
    }

    public LimitedDescription append(String line) {
        content.add(0, line);
        if (content.size() > maxItems) {
            content.removeLast();
        }
        return this;
    }

    @Override
    public String toString() {
        if (content.size() == 0) {
            return "<<empty>>";
        }

        StringBuilder out = new StringBuilder();
        List<String> reversed = Lists.reverse(content);
        for (Object item : reversed) {
            out.append(item).append("\n");
        }

        return out.toString();
    }
}
