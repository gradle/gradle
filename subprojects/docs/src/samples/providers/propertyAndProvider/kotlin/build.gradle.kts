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

open class Greeting : DefaultTask() {
    // Configurable by the user
    @get:Input
    val message: Property<String> = project.objects.property()

    // Read-only property calculated from the message
    @get:Internal
    val fullMessage: Provider<String> = message.map { it + " from Gradle" }

    @TaskAction
    fun printMessage() {
        logger.quiet(fullMessage.get())
    }
}

task<Greeting>("greeting") {
    message.set("Hi")
}
