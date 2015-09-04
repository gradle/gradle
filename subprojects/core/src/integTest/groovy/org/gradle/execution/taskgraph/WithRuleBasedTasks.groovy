/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.execution.taskgraph

trait WithRuleBasedTasks {

    String ruleBasedTasks() {
        """
        class EchoTask extends DefaultTask {
            String text = "default"
            @TaskAction
            void print() {
                println(name + ' ' + text)
            }
        }

        class ClimbTask extends DefaultTask {
            int steps = 0
            @TaskAction
            void print() {
                println "Climbing \$steps steps"
            }
        }

        class JumpTask extends DefaultTask {
            int height = 0
            @TaskAction
            void print() {
                println "Jumping \$height centimeters"
            }
        }
"""
    }
}
