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

package org.gradle.api.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.SystemProperties

class ExecutionTimeTaskConfigurationIntegrationTest extends AbstractIntegrationSpec {

    private static String NL = SystemProperties.getLineSeparator()

    def "throws decent warnings when task is configured during execution time"() {

        setup:
        def invalidConfig = """
            def anAction = new Action() {
                public void execute(Object object) {
                }
            }
            doFirst(anAction)
            doLast(anAction)
            doFirst {}
            doLast {}

            actions.set(0, anAction)
            actions.add(anAction)
            actions.addAll([anAction])

            def iter = actions.iterator()
            iter.next()
            iter.remove()

            actions.removeAll([anAction, anAction])
            actions.clear()

            onlyIf {false}
            setActions(new ArrayList())
            dependsOn bar
            dependsOn = [bar]
            setOnlyIf({false})
            setOnlyIf({false})

            Spec spec = new Spec() {
                public boolean isSatisfiedBy(Object element) {
                    return false;
                }
            };
            setOnlyIf(spec)
            onlyIf(spec)
            enabled = false
            deleteAllActions()

            inputs.file("afile")
            inputs.files("anotherfile")
            inputs.dir("aDir")
            inputs.property("propertyName", "propertyValue")
            inputs.properties(["propertyName": "propertyValue"])
            inputs.source("aSource")
            inputs.sourceDir("aSourceDir")

            outputs.upToDateWhen {false}
            outputs.upToDateWhen(spec)
            outputs.file("afile")
            outputs.files("anotherfile")
            outputs.dir("aDir")

            """
        when:
        buildFile.text = """
            task bar{
                doLast{
                    $invalidConfig
                }
            }

            task foo << {
                $invalidConfig
            }
        """
        and:
        executer.withDeprecationChecksDisabled()
        then:
        succeeds("bar", "foo")

        output.contains("Calling Task.doFirst(Action) after task execution has started has been deprecated and is scheduled to be removed in Gradle 2.0. Check the configuration of task ':bar'.$NL")
        output.contains("Calling Task.doFirst(Closure) after task execution has started has been deprecated and is scheduled to be removed in Gradle 2.0. Check the configuration of task ':bar'.$NL")
        output.contains("Calling Task.doLast(Action) after task execution has started has been deprecated and is scheduled to be removed in Gradle 2.0. Check the configuration of task ':bar'.$NL")
        output.contains("Calling Task.doLast(Closure) after task execution has started has been deprecated and is scheduled to be removed in Gradle 2.0. Check the configuration of task ':bar'.$NL")
        output.contains("Calling Task.getActions().add() after task execution has started has been deprecated and is scheduled to be removed in Gradle 2.0. Check the configuration of task ':bar'.$NL")
        output.contains("Calling Task.getActions().addAll() after task execution has started has been deprecated and is scheduled to be removed in Gradle 2.0. Check the configuration of task ':bar'.$NL")
        output.contains("Calling Task.getActions().set(int, Object) after task execution has started has been deprecated and is scheduled to be removed in Gradle 2.0. Check the configuration of task ':bar'.$NL")
        output.contains("Calling Task.getActions().remove() after task execution has started has been deprecated and is scheduled to be removed in Gradle 2.0. Check the configuration of task ':bar'.$NL")
        output.contains("Calling Task.getActions().removeAll() after task execution has started has been deprecated and is scheduled to be removed in Gradle 2.0. Check the configuration of task ':bar'.$NL")
        output.contains("Calling Task.getActions().clear() after task execution has started has been deprecated and is scheduled to be removed in Gradle 2.0. Check the configuration of task ':bar'.$NL")
        output.contains("Calling Task.onlyIf(Closure) after task execution has started has been deprecated and is scheduled to be removed in Gradle 2.0. Check the configuration of task ':bar'.$NL")
        output.contains("Calling Task.setActions(Actions<Task>) after task execution has started has been deprecated and is scheduled to be removed in Gradle 2.0. Check the configuration of task ':bar'.$NL")
        output.contains("Calling Task.dependsOn(Object...) after task execution has started has been deprecated and is scheduled to be removed in Gradle 2.0. Check the configuration of task ':bar'.$NL")
        output.contains("Calling Task.setDependsOn(Iterable) after task execution has started has been deprecated and is scheduled to be removed in Gradle 2.0. Check the configuration of task ':bar'.$NL")
        output.contains("Calling Task.setOnlyIf(Closure) after task execution has started has been deprecated and is scheduled to be removed in Gradle 2.0. Check the configuration of task ':bar'.$NL")
        output.contains("Calling Task.setOnlyIf(Spec) after task execution has started has been deprecated and is scheduled to be removed in Gradle 2.0. Check the configuration of task ':bar'.$NL")
        output.contains("Calling Task.onlyIf(Spec) after task execution has started has been deprecated and is scheduled to be removed in Gradle 2.0. Check the configuration of task ':bar'.$NL")
        output.contains("Calling Task.setEnabled(boolean) after task execution has started has been deprecated and is scheduled to be removed in Gradle 2.0. Check the configuration of task ':bar'.$NL")
        output.contains("Calling Task.deleteAllActions() after task execution has started has been deprecated and is scheduled to be removed in Gradle 2.0. Check the configuration of task ':bar'.$NL")
        output.contains("Calling TaskInputs.dir(Object) after task execution has started has been deprecated and is scheduled to be removed in Gradle 2.0. Check the configuration of task ':bar'.$NL")
        output.contains("Calling TaskInputs.files(Object...) after task execution has started has been deprecated and is scheduled to be removed in Gradle 2.0. Check the configuration of task ':bar'.$NL")
        output.contains("Calling TaskInputs.file(Object) after task execution has started has been deprecated and is scheduled to be removed in Gradle 2.0. Check the configuration of task ':bar'.$NL")
        output.contains("Calling TaskInputs.property(String, Object) after task execution has started has been deprecated and is scheduled to be removed in Gradle 2.0. Check the configuration of task ':bar'.$NL")
        output.contains("Calling TaskInputs.properties(Map) after task execution has started has been deprecated and is scheduled to be removed in Gradle 2.0. Check the configuration of task ':bar'.$NL")
        output.contains("Calling TaskInputs.source(Object) after task execution has started has been deprecated and is scheduled to be removed in Gradle 2.0. Check the configuration of task ':bar'.$NL")
        output.contains("Calling TaskInputs.sourceDir(Object) after task execution has started has been deprecated and is scheduled to be removed in Gradle 2.0. Check the configuration of task ':bar'.$NL")
        output.contains("Calling TaskOutputs.upToDateWhen(Closure) after task execution has started has been deprecated and is scheduled to be removed in Gradle 2.0. Check the configuration of task ':bar'.$NL")
        output.contains("Calling TaskOutputs.upToDateWhen(Spec) after task execution has started has been deprecated and is scheduled to be removed in Gradle 2.0. Check the configuration of task ':bar'.$NL")
        output.contains("Calling TaskOutputs.file(Object) after task execution has started has been deprecated and is scheduled to be removed in Gradle 2.0. Check the configuration of task ':bar'.$NL")
        output.contains("Calling TaskOutputs.files(Object...) after task execution has started has been deprecated and is scheduled to be removed in Gradle 2.0. Check the configuration of task ':bar'.$NL")
        output.contains("Calling TaskOutputs.dir(Object) after task execution has started has been deprecated and is scheduled to be removed in Gradle 2.0. Check the configuration of task ':bar'.$NL")
        and:

        output.contains("Calling Task.doFirst(Action) after task execution has started has been deprecated and is scheduled to be removed in Gradle 2.0. Check the configuration of task ':foo'. You may have misused '<<' at task declaration.$NL")
        output.contains("Calling Task.doFirst(Closure) after task execution has started has been deprecated and is scheduled to be removed in Gradle 2.0. Check the configuration of task ':foo'. You may have misused '<<' at task declaration.$NL")
        output.contains("Calling Task.doLast(Action) after task execution has started has been deprecated and is scheduled to be removed in Gradle 2.0. Check the configuration of task ':foo'. You may have misused '<<' at task declaration.$NL")
        output.contains("Calling Task.doLast(Closure) after task execution has started has been deprecated and is scheduled to be removed in Gradle 2.0. Check the configuration of task ':foo'. You may have misused '<<' at task declaration.$NL")
        output.contains("Calling Task.getActions().add() after task execution has started has been deprecated and is scheduled to be removed in Gradle 2.0. Check the configuration of task ':foo'. You may have misused '<<' at task declaration.$NL")
        output.contains("Calling Task.getActions().addAll() after task execution has started has been deprecated and is scheduled to be removed in Gradle 2.0. Check the configuration of task ':foo'. You may have misused '<<' at task declaration.$NL")
        output.contains("Calling Task.getActions().set(int, Object) after task execution has started has been deprecated and is scheduled to be removed in Gradle 2.0. Check the configuration of task ':foo'. You may have misused '<<' at task declaration.$NL")
        output.contains("Calling Task.getActions().remove() after task execution has started has been deprecated and is scheduled to be removed in Gradle 2.0. Check the configuration of task ':foo'. You may have misused '<<' at task declaration.$NL")
        output.contains("Calling Task.getActions().removeAll() after task execution has started has been deprecated and is scheduled to be removed in Gradle 2.0. Check the configuration of task ':foo'. You may have misused '<<' at task declaration.$NL")
        output.contains("Calling Task.getActions().clear() after task execution has started has been deprecated and is scheduled to be removed in Gradle 2.0. Check the configuration of task ':foo'. You may have misused '<<' at task declaration.$NL")
        output.contains("Calling Task.onlyIf(Closure) after task execution has started has been deprecated and is scheduled to be removed in Gradle 2.0. Check the configuration of task ':foo'. You may have misused '<<' at task declaration.$NL")
        output.contains("Calling Task.setActions(Actions<Task>) after task execution has started has been deprecated and is scheduled to be removed in Gradle 2.0. Check the configuration of task ':foo'. You may have misused '<<' at task declaration.$NL")
        output.contains("Calling Task.dependsOn(Object...) after task execution has started has been deprecated and is scheduled to be removed in Gradle 2.0. Check the configuration of task ':foo'. You may have misused '<<' at task declaration.$NL")
        output.contains("Calling Task.setDependsOn(Iterable) after task execution has started has been deprecated and is scheduled to be removed in Gradle 2.0. Check the configuration of task ':foo'. You may have misused '<<' at task declaration.$NL")
        output.contains("Calling Task.setOnlyIf(Closure) after task execution has started has been deprecated and is scheduled to be removed in Gradle 2.0. Check the configuration of task ':foo'. You may have misused '<<' at task declaration.$NL")
        output.contains("Calling Task.setOnlyIf(Spec) after task execution has started has been deprecated and is scheduled to be removed in Gradle 2.0. Check the configuration of task ':foo'. You may have misused '<<' at task declaration.$NL")
        output.contains("Calling Task.onlyIf(Spec) after task execution has started has been deprecated and is scheduled to be removed in Gradle 2.0. Check the configuration of task ':foo'. You may have misused '<<' at task declaration.$NL")
        output.contains("Calling Task.setEnabled(boolean) after task execution has started has been deprecated and is scheduled to be removed in Gradle 2.0. Check the configuration of task ':foo'. You may have misused '<<' at task declaration.$NL")
        output.contains("Calling Task.deleteAllActions() after task execution has started has been deprecated and is scheduled to be removed in Gradle 2.0. Check the configuration of task ':foo'. You may have misused '<<' at task declaration.$NL")
        output.contains("Calling TaskInputs.dir(Object) after task execution has started has been deprecated and is scheduled to be removed in Gradle 2.0. Check the configuration of task ':foo'. You may have misused '<<' at task declaration.$NL")
        output.contains("Calling TaskInputs.files(Object...) after task execution has started has been deprecated and is scheduled to be removed in Gradle 2.0. Check the configuration of task ':foo'. You may have misused '<<' at task declaration.$NL")
        output.contains("Calling TaskInputs.file(Object) after task execution has started has been deprecated and is scheduled to be removed in Gradle 2.0. Check the configuration of task ':foo'. You may have misused '<<' at task declaration.$NL")
        output.contains("Calling TaskInputs.property(String, Object) after task execution has started has been deprecated and is scheduled to be removed in Gradle 2.0. Check the configuration of task ':foo'. You may have misused '<<' at task declaration.$NL")
        output.contains("Calling TaskInputs.properties(Map) after task execution has started has been deprecated and is scheduled to be removed in Gradle 2.0. Check the configuration of task ':foo'. You may have misused '<<' at task declaration.$NL")
        output.contains("Calling TaskInputs.source(Object) after task execution has started has been deprecated and is scheduled to be removed in Gradle 2.0. Check the configuration of task ':foo'. You may have misused '<<' at task declaration.$NL")
        output.contains("Calling TaskInputs.sourceDir(Object) after task execution has started has been deprecated and is scheduled to be removed in Gradle 2.0. Check the configuration of task ':foo'. You may have misused '<<' at task declaration.$NL")
        output.contains("Calling TaskOutputs.upToDateWhen(Closure) after task execution has started has been deprecated and is scheduled to be removed in Gradle 2.0. Check the configuration of task ':foo'. You may have misused '<<' at task declaration.$NL")
        output.contains("Calling TaskOutputs.upToDateWhen(Spec) after task execution has started has been deprecated and is scheduled to be removed in Gradle 2.0. Check the configuration of task ':foo'. You may have misused '<<' at task declaration.$NL")
        output.contains("Calling TaskOutputs.file(Object) after task execution has started has been deprecated and is scheduled to be removed in Gradle 2.0. Check the configuration of task ':foo'. You may have misused '<<' at task declaration.$NL")
        output.contains("Calling TaskOutputs.files(Object...) after task execution has started has been deprecated and is scheduled to be removed in Gradle 2.0. Check the configuration of task ':foo'. You may have misused '<<' at task declaration.$NL")
        output.contains("Calling TaskOutputs.dir(Object) after task execution has started has been deprecated and is scheduled to be removed in Gradle 2.0. Check the configuration of task ':foo'. You may have misused '<<' at task declaration.$NL")
    }
}
