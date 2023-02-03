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
package org.gradle.api.file;

/**
 * The EmptyFileVisitor can be extends by implementations that only require to implement one of the 2 visit methods
 * (dir or file). This is just to limit the amount of code clutter when not both visit methods need to be implemented.
 */
public class EmptyFileVisitor implements FileVisitor {

    @Override
    public void visitDir(FileVisitDetails dirDetails) { }

    @Override
    public void visitFile(FileVisitDetails fileDetails) { }
}
