package org.gradle.integtests

class GradleRun {
    String id
    String execute
    int debugLevel
    String file
    String subDir
    boolean groovyScript = false
    List envs = []
    String outputFile

    def withLoggingLevel(int debugLevel) {
        this.debugLevel = debugLevel
        this
    }
}