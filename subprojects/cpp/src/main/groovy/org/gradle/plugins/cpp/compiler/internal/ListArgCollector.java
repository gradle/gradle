/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.plugins.cpp.compiler.internal;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class ListArgCollector implements ArgCollector {
    
    private final List<List<String>> groups = new LinkedList<List<String>>();

    public ArgCollector args(Object... args) {
        List<String> group = new ArrayList<String>(args.length);
        for (Object arg : args) {
            group.add(arg.toString());
        }
        groups.add(group);
        
        return this;
    }
    
    public List<List<String>> getGroups() {
        return groups;
    }
    
    public List<String> getFlattened() {
        List<String> flattened = new LinkedList<String>();
        for (List<String> group : groups) {
            for (String arg : group) {
                flattened.add(arg);
            }
        }

        return flattened;
    }
    
}
