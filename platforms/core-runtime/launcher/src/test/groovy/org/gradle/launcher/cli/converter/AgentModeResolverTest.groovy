/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.launcher.cli.converter

import org.gradle.cli.CommandLineParser
import spock.lang.Specification

import static org.gradle.launcher.cli.converter.AgentModeResolver.AgentMode.ENABLED
import static org.gradle.launcher.cli.converter.AgentModeResolver.AgentMode.NOT_REQUESTED

class AgentModeResolverTest extends Specification {

    def "resolves #expected for args #args, env var #envVar and property #property"() {
        expect:
        resolve(args, envVar, property) == expected

        where:
        args           | envVar  | property | expected
        []             | null    | null     | NOT_REQUESTED
        []             | null    | "true"   | ENABLED
        []             | null    | "false"  | NOT_REQUESTED
        []             | "true"  | null     | ENABLED
        []             | "TRUE"  | null     | ENABLED
        []             | "1"     | null     | NOT_REQUESTED
        []             | "true"  | "false"  | ENABLED
        []             | "false" | "true"   | NOT_REQUESTED
        ["--agent"]    | null    | null     | ENABLED
        ["--agent"]    | "false" | "false"  | ENABLED
        ["--no-agent"] | "true"  | "true"   | NOT_REQUESTED
    }

    private static AgentModeResolver.AgentMode resolve(List<String> args, String envVar, String property) {
        def resolver = new AgentModeResolver()
        def parser = new CommandLineParser()
        resolver.configure(parser)

        def properties = new HashMap<String, String>()
        if (property != null) {
            properties["org.gradle.agent"] = property
        }
        def environment = envVar == null ? [:] : ["ORG_GRADLE_AGENT": envVar]
        return resolver.resolve(parser.parse(args), properties, environment)
    }
}
