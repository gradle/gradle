/*
 * Copyright 2018 the original author or authors.
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

task("print") {
    doLast {
        val list: ListProperty<String> = project.objects.listProperty()

        // Resolve the list
        logger.quiet("The list contains: " + list.get())

        // Add elements to the empty list
        list.add(project.provider { "element-1" })  // Add a provider element
        list.add("element-2")                       // Add a concrete element

        // Resolve the list
        logger.quiet("The list contains: " + list.get())

        // Overwrite the entire list with a new list
        list.set(listOf("element-3", "element-4"))

        // Resolve the list
        logger.quiet("The list contains: " + list.get())

        // Add more elements through a list provider
        list.addAll(project.provider { listOf("element-5", "element-6") })

        // Resolve the list
        logger.quiet("The list contains: " + list.get())
    }
}
