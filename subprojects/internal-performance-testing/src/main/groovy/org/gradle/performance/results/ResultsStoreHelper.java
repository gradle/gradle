/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.performance.results;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ResultsStoreHelper {
    private static final String SYSPROP_PERFORMANCE_TEST_CHANNEL = "org.gradle.performance.execution.channel";
    private static final Logger LOGGER = LoggerFactory.getLogger(ResultsStoreHelper.class);

    public static List<String> split(String string) {
        if (null != string) {
            return ImmutableList.copyOf(Splitter.on(",").split(string));
        }
        return Collections.emptyList();
    }

    public static String[] toArray(List<String> list) {
        return list == null ? null : list.toArray(new String[0]);
    }

    public static List<String> toList(Object object) {
        Object[] value = (Object[]) object;
        if (value == null) {
            return null;
        }
        List<String> result = new ArrayList<String>(value.length);
        for (Object aValue : value) {
            result.add(aValue.toString());
        }
        return result;
    }

    public static String determineChannel() {
        return System.getProperty(SYSPROP_PERFORMANCE_TEST_CHANNEL, "commits");
    }
}
