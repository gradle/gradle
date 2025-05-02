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

package org.gradle.test.fixtures.concurrent

/**
 * A dynamic collection of {@link NamedOperation} instances. When a method is called that takes a single Runnable as parameter, a new operation is defined.
 * When a property is accessed, queries an existing operation, asserting that it exists. For example:
 *
 * <pre>
 *  when:
 *  // runs the given closure and defines operation 'doStuff'
 *  operation.doStuff {
 *      // do some test action
 *  }
 *
 *  then:
 *  // query
 *  operation.doStuff.end > operation.doStuff.start
 *  instant.doStuff == operation.doStuff.end
 * </pre>
 */
class Operations {
    private final InstantFactory instantFactory
    private final Object lock = new Object()
    private final Map<String, NamedOperation> operations = [:]
    private final OperationListener listener

    Operations(InstantFactory instantFactory, OperationListener listener) {
        this.listener = listener
        this.instantFactory = instantFactory
    }

    public def propertyMissing(String name) {
        synchronized (lock) {
            if (!operations.containsKey(name)) {
                throw new IllegalStateException("Operation '$name' has not been defined by any test thread.")
            }
            return operations[name]
        }
    }

    public def methodMissing(String name, def args) {
        if (args.length != 1 || !(args[0] instanceof Runnable)) {
            throw new MissingMethodException(name, getClass(), args)
        }
        def action = args[0]
        def operation
        synchronized (lock) {
            if (operations.containsKey(name)) {
                throw new IllegalStateException("Operation '$name' has already been defined.")
            }
            operation = new NamedOperation(name)
            operations[name] = operation
        }
        listener.operationStarted()
        try {
            action.run()
        } finally {
            operation.completed(instantFactory.now(operation.name))
            listener.operationFinished()
        }
    }
}
