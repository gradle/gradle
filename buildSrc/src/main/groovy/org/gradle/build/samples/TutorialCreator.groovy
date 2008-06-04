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

package org.gradle.build.samples

import org.gradle.build.integtests.Executer

/**
 * @author Hans Dockter
 */
class TutorialCreator {
    static String NL = System.properties['line.separator']

    static Map scripts() {
        String hello = "Hello world!"
        String date = 'Today is: ${new Date()}'
        String intro = "I'm Gradle"
        Map scripts = [:]

        scripts['hello'] = ["""createTask('hello') {
    println '$hello'
}""", {assert "$hello$NL" == it}]

        scripts['date'] = ["""createTask('date') {
    println "$date"
}""", {assert it.startsWith(date[0..8])}]

        scripts['count'] = ['''createTask('count') {
    4.times { print "$it " }
}''', {assert it == '0 1 2 3 '}]

        scripts['intro'] = ["""${scripts.hello[0]}
createTask('intro', dependsOn: 'hello') {
    println "$intro"
}""", {assert it == ("$hello$NL$intro$NL")}]

        String dynamicText = "I'm task number "
        scripts['dynamic'] = ["""4.times { counter ->
    createTask(\"task_\$counter\") {
        println \"$dynamicText\$counter\"
    }
}""", {assert it == "${dynamicText}1$NL"}, "task_1"]

        // TODO: We assume an order of the dependsOn execution for the assert. This order is not guaranteed. This is a fragile test smell.
        scripts['dynamicDepends'] = ["""${scripts.dynamic[0]}
task('task_0').dependsOn 'task_2', 'task_3'
""", {assert it == "${dynamicText}2${NL}${dynamicText}3${NL}${dynamicText}0${NL}"}, "task_0"]

        String earth = "Hello Earth"
        String venus = "Hello Venus"
        String mars = "Hello Mars"
        scripts['helloEnhanced'] = ["""createTask('hello') {
    println '$earth'
}
task('hello').doFirst {
    println '$venus'
}
task('hello').doLast {
    println '$mars'
}""", {assert it == ("$venus$NL$earth$NL$mars$NL")}, "hello"]

        String helloAgain = "Hello again!"
        scripts['helloWithShortCut'] = ["""${scripts.hello[0]}
hello.doLast {
    println '$helloAgain'
}""", {assert it == ("$hello$NL$helloAgain$NL")}, "hello"]

        String taskX = "taskX"
        String taskY = "taskY"
        scripts['lazyDependsOn'] = ["""createTask('taskX', dependsOn: 'taskY') {
    println 'taskX'
}
createTask('taskY') {
    println 'taskY'
}""", {assert "$taskY$NL$taskX$NL" == it}, "taskX"]

        scripts['antChecksum'] = ["""File[] userDir = new File(System.getProperty('user.dir')).listFiles()
int fileNumber = userDir.size() >= 5 ? 5 : userDir.size()

createTask('checksum') {
    userDir[0..fileNumber].each { File file ->
        ant.checksum(file: file.canonicalPath, property: file.name)
        println "\$file.name Checksum: \${ant.antProject.properties[file.name]}"
    }
}
        """, {/* just a smoke test */}, "checksum"]

        scripts['antChecksumWithMethod'] = ["""createTask('checksum') {
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
}""", {/* just a smoke test */}, "checksum"]

        String mySkipProperty = 'mySkipProperty'
        scripts['skipProperties'] = ["""createTask('skipMe') {
    println 'This should not be printed if the $mySkipProperty system property is set to true.'
}.skipProperties << 'mySkipProperty'""", {assert "" == it}, "-D$mySkipProperty=true skipMe"]

        String autoskipTaskname = 'autoskip'
        scripts[autoskipTaskname] = ["""createTask('$autoskipTaskname') {
    println 'This should not be printed if the skip.$autoskipTaskname system property is set.'
}""", {assert "" == it}, "-Dskip.$autoskipTaskname $autoskipTaskname"]
// *****************
        scripts["${autoskipTaskname}Depends"] = ["""createTask('$autoskipTaskname') {
    println 'This should not be printed if the skip.$autoskipTaskname system property is set.'
}
createTask('depends', dependsOn: '$autoskipTaskname') {
    println "This should not be printed if the skip.$autoskipTaskname system property is set."
}.skipProperties << 'skip.$autoskipTaskname'
""", {assert "" == it}, "-Dskip.$autoskipTaskname depends"]
// *****************
        String unaffectedMessage = "I am not affected"
        scripts["stopExecutionException"] = ["""createTask('compile') {
    println 'We are doing the compile.'
}

compile.doFirst {
    // Here you would put arbitrary conditions in real life. But we use this as an integration test, so we want defined behavior.
    if (true) { throw new StopExecutionException() }
}
createTask('myTask', dependsOn: 'compile') {
   println '$unaffectedMessage'
}
""", {assert "$unaffectedMessage$NL" == it}, "myTask"]
// *****************
        String releaseText = 'We release now'
        String distributionText = 'We build the zip with version='
        scripts["configureByDag"] = ["""version = null
configureByDag = {dag ->
    if (dag.hasTask(':release')) {
        version = '1.0'
    } else {
        version = '1.0-SNAPSHOT'
    }
}
createTask('distribution') {
    println \"$distributionText\$version\"     
}
createTask('release', dependsOn: 'distribution') {
    println '$releaseText'
}
""", {assert "${distributionText}1.0$NL$releaseText$NL" == it}, "release"]
// *****************
        String directoryNotFoundStatement = 'The class directory does not exists. I can not operate'
        scripts["mkdirTrap"] = ["""classesDir = new File('build/classes')
classesDir.mkdirs()
createTask('clean') {
    ant.delete(dir: 'build')
}
createTask('compile', dependsOn: 'clean') {
    if (!classesDir.isDirectory()) {
        println '$directoryNotFoundStatement'
        // do something
    }
    // do something
}
""", {assert "${directoryNotFoundStatement}$NL" == it}, "compile"]
// *****************
        String directoryFoundStatement = 'The class directory exists. I can operate'
        scripts["makeDirectory"] = ["""classesDir = new File('build/classes')
createTask('resources') {
    classesDir.mkdirs()
    // do something
}
createTask('compile', dependsOn: 'resources') {
    if (classesDir.isDirectory()) {
        println '$directoryFoundStatement'
    }
    // do something
}
""", {assert "${directoryFoundStatement}$NL" == it}, "compile"]
// *****************
        scripts["directoryTask"] = ["""classes = dir('build/classes')
createTask('resources', dependsOn: classes) {
    // do something
}
createTask('otherResources', dependsOn: classes) {
    if (classes.dir.isDirectory()) {
        println '$directoryFoundStatement'
    }
    // do something
}
""", {assert "${directoryFoundStatement}$NL" == it}, "otherResources"]
// *****************
        scripts["pluginIntro"] = ["""usePlugin('java')

createTask('check') {
    println(task('compile').destinationDir.name) // We could also write println(compile)
}""", {assert "classes$NL" == it}, "check"]
// *****************
        scripts["pluginConfig"] = ["""usePlugin('java')

createTask('check') {
    resources.destinationDir = new File(buildDir, 'output')
    println(resources.destinationDir.name)
    println(compile.destinationDir.name)
}""", {assert "output${NL}classes$NL" == it}, "check"]
// *****************
        scripts["pluginConvention"] = ["""usePlugin('java')

createTask('check') {
    classesDirName = 'output'
    println(resources.destinationDir.name)
    println(compile.destinationDir.name)
    println(convention.classesDirName)
}""", {assert "output${NL}output${NL}output$NL" == it}, "check"]
// *****************
        String newTaskMessage = "I am the new one." 
        scripts["replaceTask"] = ["""createTask('resources', type: Resources)

createTask('resources', overwrite: true) {
    println('$newTaskMessage')
}""", {assert "$newTaskMessage${NL}" == it}, "resources"]
// *****************
        scripts["projectApi"] = ["""createTask('check') {
    println project.name
    println name
}""", {assert "tutorial${NL}tutorial${NL}" == it}, "check"]
// *****************


        scripts

    }

    static void writeScripts(File baseDir) {
        scripts().each {entry ->
            new File(baseDir, "${entry.key}file").withPrintWriter() {PrintWriter writer -> writer.write(entry.value[0])}
        }
    }

    static void createOutput(String gradleHome, File tutorialDir, File outputDir) {
        outputDir.mkdirs()
        scripts().each {entry ->
            String taskName = entry.value.size < 3 ? entry.key : entry.value[2]
            Userguide.createOutput(new File(outputDir, "${entry.key}file.out"),
                    gradleHome,
                    tutorialDir,
                    [taskName],
                    "${entry.key}file",
                    Executer.QUIET)
        }
    }

}