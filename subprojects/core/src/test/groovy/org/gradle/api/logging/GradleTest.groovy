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
package org.gradle.api.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.LoggingEvent
import ch.qos.logback.core.OutputStreamAppender
import ch.qos.logback.core.layout.EchoLayout
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.slf4j.LoggerFactory
import org.slf4j.Marker

/**
 * Gradle logger AST transformation unit tests.
 *
 * @author Benjamin Muschko
 */
class GradleTest extends GroovyTestCase {
    LogbackInterceptingAppender appender
    org.slf4j.Logger logger

    @Before
    void setUp() {
        super.setUp()
        logger = LoggerFactory.getLogger("MyClass")

        appender = new LogbackInterceptingAppender()
        appender.setOutputStream(new ByteArrayOutputStream())
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory()
        appender.setContext(lc)
        appender.setName("MyAppender")
        appender.setLayout(new EchoLayout())
        appender.start()

        logger.addAppender(appender)
        logger.setLevel(Level.ALL)
    }

    @After
    void tearDown() {
        super.tearDown()
        logger.detachAppender(appender)
    }

    @Test
    void testPrivateFinalStaticLogFieldAppears() {
        Class clazz = new GroovyClassLoader().parseClass('''
            @org.gradle.api.logging.Gradle
            class MyClass {
            } ''')

        assert clazz.declaredFields.find { Field field ->
            field.name == "log" &&
                  Modifier.isPrivate(field.getModifiers()) &&
                  Modifier.isStatic(field.getModifiers()) &&
                  Modifier.isTransient(field.getModifiers()) &&
                  Modifier.isFinal(field.getModifiers())
        }
    }

    @Test
    void testPrivateFinalStaticNamedLogFieldAppears() {
        Class clazz = new GroovyClassLoader().parseClass('''
            @org.gradle.api.logging.Gradle('logger')
            class MyClass {
            } ''')

        assert clazz.declaredFields.find { Field field ->
            field.name == "logger" &&
                  Modifier.isPrivate(field.getModifiers()) &&
                  Modifier.isStatic(field.getModifiers()) &&
                  Modifier.isTransient(field.getModifiers()) &&
                  Modifier.isFinal(field.getModifiers())
        }
    }

    @Test
    void testClassAlreadyHasLogField() {
        shouldFail {
            Class clazz = new GroovyClassLoader().parseClass('''
                @org.gradle.api.logging.Gradle
                class MyClass {
                    String log
                } ''')

            assert clazz.newInstance()
        }
    }

    @Test
    void testClassAlreadyHasNamedLogField() {
        shouldFail {
            Class clazz = new GroovyClassLoader().parseClass('''
                @org.gradle.api.logging.Gradle('logger')
                class MyClass {
                    String logger
                } ''')

            assert clazz.newInstance()
        }
    }

    @Test
    void testLogInfo() {
        Class clazz = new GroovyClassLoader().parseClass('''
          @org.gradle.api.logging.Gradle
          class MyClass {
              def loggingMethod() {
                  log.quiet ("quiet called")
                  log.error ("error called")
                  log.warn  ("warn called")
                  log.lifecycle  ("lifecycle called")
                  log.info  ("info called")
                  log.debug ("debug called")
                  log.trace ("trace called")
              }
          }
          new MyClass().loggingMethod() ''')

        Script s = (Script) clazz.newInstance()
        s.run()

        def events = appender.getEvents()
        int ind = 0
        assert events.size() == 7
        assert matchesEvent(events[ind], Level.INFO, "quiet called", Logging.QUIET) == true
        assert matchesEvent(events[++ind], Level.ERROR, "error called", null) == true
        assert matchesEvent(events[++ind], Level.WARN, "warn called", null) == true
        assert matchesEvent(events[++ind], Level.INFO, "lifecycle called", Logging.LIFECYCLE) == true
        assert matchesEvent(events[++ind], Level.INFO, "info called", null) == true
        assert matchesEvent(events[++ind], Level.DEBUG, "debug called", null) == true
        assert matchesEvent(events[++ind], Level.TRACE, "trace called", null) == true
    }

    @Test
    void testLogFromStaticMethods() {
        Class clazz = new GroovyClassLoader().parseClass("""
            @org.gradle.api.logging.Gradle
            class MyClass {
                static loggingMethod() {
                  log.info   ("(static) info called")
                }
            }
            MyClass.loggingMethod()""")

        Script s = (Script) clazz.newInstance()
        s.run()

        def events = appender.getEvents()
        int ind = 0
        assert events.size() == 1
        assert matchesEvent(events[ind], Level.INFO, "(static) info called", null) == true
    }

    @Test
    void testLogInfoWithNamedLogger() {
        Class clazz = new GroovyClassLoader().parseClass('''
              @org.gradle.api.logging.Gradle('logger')
              class MyClass {

                  def loggingMethod() {
                      logger.quiet ("quiet called")
                      logger.error ("error called")
                      logger.warn  ("warn called")
                      logger.lifecycle  ("lifecycle called")
                      logger.info  ("info called")
                      logger.debug ("debug called")
                      logger.trace ("trace called")
                  }
              }
              new MyClass().loggingMethod() ''')

        Script s = (Script) clazz.newInstance()
        s.run()

        def events = appender.getEvents()
        int ind = 0
        assert events.size() == 7
        assert matchesEvent(events[ind], Level.INFO, "quiet called", Logging.QUIET) == true
        assert matchesEvent(events[++ind], Level.ERROR, "error called", null) == true
        assert matchesEvent(events[++ind], Level.WARN, "warn called", null) == true
        assert matchesEvent(events[++ind], Level.INFO, "lifecycle called", Logging.LIFECYCLE) == true
        assert matchesEvent(events[++ind], Level.INFO, "info called", null) == true
        assert matchesEvent(events[++ind], Level.DEBUG, "debug called", null) == true
        assert matchesEvent(events[++ind], Level.TRACE, "trace called", null) == true
    }

    class LogbackInterceptingAppender<E> extends OutputStreamAppender<E> {

        List<LoggingEvent> events = new ArrayList<LoggingEvent>()

        public List<LoggingEvent> getEvents() {
            return events
        }

        protected void append(E event) {
            if (event instanceof LoggingEvent) {
                events.add(event)
            } else {
                throw new RuntimeException("Unable to intercept logging events - probably API has changed")
            }
            super.append(event)
        }
    }

    private boolean matchesEvent(LoggingEvent event, Level level, String text, Marker marker) {
        event.getLevel().equals(level) && event.getMessage().equals(text) && event.getMarker() == marker
    }
}