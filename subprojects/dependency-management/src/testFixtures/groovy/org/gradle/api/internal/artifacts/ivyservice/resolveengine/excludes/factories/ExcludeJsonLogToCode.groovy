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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.factories

import groovy.json.JsonSlurper

class ExcludeJsonLogToCode {
    private final static List<String> WORDS = [
        "shake",
        "grain",
        "root",
        "rain",
        "cover",
        "crib",
        "lunchroom",
        "sort",
        "building",
        "fact",
        "grass",
        "planes",
        "stove",
        "pull",
        "calculator",
        "suggestion",
        "beginner",
        "plough",
        "insurance",
        "hat",
        "toys",
        "plant",
        "trail",
        "wing",
        "ring",
        "desk",
        "yak",
        "teaching",
        "street",
        "cattle",
        "sun",
        "wealth",
        "cabbage",
        "playground",
        "wren",
        "flowers",
        "cobweb",
        "shame",
        "plate",
        "authority",
        "cave",
        "floor",
        "shelf",
        "snakes",
        "ants",
        "comparison",
        "quicksand",
        "eyes",
        "thumb",
        "church",
        "needle",
        "celery",
        "competition",
        "metal",
        "box",
        "industry",
        "brother",
        "deer",
        "waves",
        "advice",
        "acoustics",
        "nut",
        "store",
        "finger"
    ]

    private final Map<String, String> mappingCache = [:].withDefault {
        def size = mappingCache.size()
        size >WORDS.size() ? "word${size}" : WORDS[size]
    }

    /**
     * Converts an operation dumped as JSON into code which can be used to
     * check the result, to be used in {@link NormalizingExcludeFactory}
     *
     * @param json
     * @return
     */
    String toCode(String json) {
        def slurper = new JsonSlurper().parse(json.trim().getBytes("utf-8"))
        toCodeFromSlurpedJson(slurper)
    }

    String toCodeFromSlurpedJson(Object parsed) {
        def operation = parsed.operation.name
        def operands = parsed.operation.operands.collect { e ->
            "${toExclude(e)}"
        }
        """
${operands.indexed().collect { i, e -> "def operand$i = $e" }.join("\n")}
def operation = factory.$operation([
   ${operands.indexed().collect { i, e -> "operand$i" }.join(",\n   ")}
] as Set)
"""
    }

    private String toExclude(it) {
        if (it == 'excludes everything') {
            return 'everything()'
        }
        assert it instanceof Map
        assert it.keySet().size() == 1
        String key = it.keySet()[0]
        Object value = it[key]
        toExclude(key, value)
    }

    private void collectOperands(Object value, List<String> collector) {
        if (value instanceof List) {
            value.each {
                collectOperands(it, collector)
            }
        } else {
            collector << toExclude(value)
        }
    }

    private String toExclude(String opType, Object value) {
        switch (opType) {
            case "any of":
                def operands = []
                collectOperands(value, operands)
                return "anyOf(${operands.join(', ')})"
            case "all of":
                def operands = []
                collectOperands(value, operands)
                return "allOf(${operands.join(', ')})"
            case "exclude group":
                return "group('${anonymize(value)}')"
                break
            case "exclude module":
                return "module('${anonymize(value)}')"
            case "exclude module id":
                def (group, name) = value.split(':')
                return "moduleId('${anonymize(group)}', '${anonymize(name)}')"
            case "module names":
                return "moduleSet(${value.collect { "'${anonymize(it)}'" }.join(', ')})"
            case "module ids":
                return "moduleIdSet(${value.collect { "'${anonymize(it)}'" }.join(', ')})"
            case "groups":
                return "groupSet(${value.collect { "'${anonymize(it)}'" }.join(', ')})"
            default:
                throw new UnsupportedOperationException("Not yet implemented for $opType")
        }
    }

    private String anonymize(String value) {
        value
        value.split(":").collect {
            mappingCache[it]
        }.join(':')
    }

    static void main(String[] args) {
        def code = new ExcludeJsonLogToCode().toCode("""

PASTE SOME JSON HERE

""")

        println(code)
    }
}
