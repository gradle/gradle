/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.integtest

/**
 * @author Hans Dockter
 */
class TutorialCreator {
    Map scripts() {
        String NL = System.properties['line.separator']
        String hello = "Hello world!"
        String date = 'Today is: ${new Date()}'
        String intro = "I'm Gradle"
        Map scripts = [:]


        scripts['hello'] = ["""
createTask('hello') {
    println '$hello'
}
""", {assert "$hello$NL" == it}]

        scripts['date'] = ["""
createTask('date') {
    println "$date"
}
""", {assert it.startsWith(date[0..8])}]

        scripts['count'] = ['''
createTask('count') {
    4.times { print "$it " }
}
''', {assert it == '0 1 2 3 '}]

        scripts['intro'] = ["""
    ${scripts.hello[0]}
createTask('intro', dependsOn: 'hello') {
    println "$intro"
}
""", {assert it == ("$hello$NL$intro$NL")}]

        String dynamicText = "I'm task number "
        scripts['dynamic'] = ["""
4.times { counter ->
    createTask(\"task_\$counter\") {
        println \"$dynamicText\$counter\"
    }
}
""", {assert it == "${dynamicText}1$NL"}, "task_1"]

        // TODO: We assume an order of the dependsOn execution for the assert. This order is not guaranteed. This is a fragile test smell.
        scripts['dynamicDepends'] = ["""
${scripts.dynamic[0]}
task('task_0').dependsOn 'task_2', 'task_3'
""", {assert it == "${dynamicText}2${NL}${dynamicText}3${NL}${dynamicText}0${NL}"}, "task_0"]

        String earth = "Hello Earth"
        String venus = "Hello Venus"
        String mars = "Hello Mars"
        scripts['helloEnhanced'] = ["""
createTask('hello') {
    println '$earth'
}
task('hello').doFirst {
    println '$venus'
}
task('hello').doLast {
    println '$mars'
}
""", {assert it == ("$venus$NL$earth$NL$mars$NL")}, "hello"]

        String helloAgain = "Hello again!"
        scripts['helloWithShortCut'] = ["""
${scripts.hello[0]}
hello.doLast {
    println '$helloAgain'
}
""", {assert it == ("$hello$NL$helloAgain$NL")}, "hello"]

        String taskX = "taskX"
        String taskY = "taskY"
        scripts['lazyDependsOn'] = ["""
createTask('taskX', dependsOn: 'taskY') {
    println 'taskX'
}
createTask('taskY') {
    println 'taskY'
}
""", {assert "$taskY$NL$taskX$NL" == it}, "taskX"]

        scripts['antChecksum'] = ["""
File[] userDir = new File(System.getProperty('user.dir')).listFiles()
int fileNumber = userDir.size() >= 5 ? 5 : userDir.size()

createTask('checksum') {
    userDir[0..fileNumber].each { File file ->
        ant.checksum(file: file.canonicalPath, property: file.name)
        println "\$file.name Checksum: \${ant.antProject.properties[file.name]}"
    }
}
        """, {/* just a smoke test */}, "checksum"]

        scripts['antChecksumWithMethod'] = ["""
createTask('checksum') {
    fileList('user.dir').each { File file ->
        ant.checksum(file: file.canonicalPath, property: file.name)
        println "\$file.name Checksum: \${ant.antProject.properties[file.name]}"
    }
}

createTask('length') {
    fileList('user.dir').each { File file ->
        ant.length(file: file.canonicalPath, property: file.name)
        println "\$file.name Length: \${ant.antProject.properties[file.name]}"
    }
}

File[] fileList(String property) {
    File[] dir = new File(System.getProperty(property)).listFiles()
    int fileNumber = dir.size() >= 5 ? 5 : dir.size()
    dir[0..fileNumber]
}
        """, {/* just a smoke test */}, "checksum"]

        scripts
    }

    void writeScripts(File baseDir) {
        scripts().each {entry ->
            new File(baseDir, "${entry.key}.groovy").withPrintWriter() {PrintWriter writer -> writer.write(entry.value[0])}
        }
    }

}