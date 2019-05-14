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

package org.gradle.performance.results;

import com.google.common.collect.ImmutableMap;
import groovy.json.JsonOutput;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Graph {
    String id;
    String title;
    List<Line> data;

    public Graph(String id, String title, Line... lines) {
        this.id = id;
        this.title = title;
        this.data = Arrays.asList(lines);
    }

    String getData() {
        return JsonOutput.toJson(data);
    }

    String getOptions() {
        List<List<Object>> ticks = IntStream.range(0, data.get(0).data.size())
            .mapToObj(index -> Arrays.<Object>asList(index, index))
            .collect(Collectors.toList());
        return JsonOutput.toJson(ImmutableMap.of("xaxis", ImmutableMap.of("ticks", ticks)));
    }

    void render(HtmlPageGenerator.MetricsHtml html) {
        html.h3().text(title).end();
        html.div().id(id).classAttr("chart").end();
        html.script().raw(String.format("$.plot('#%s', %s, %s)", id, getData(), getOptions())).end();
    }
}
