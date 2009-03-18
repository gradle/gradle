package org.gradle.integtests

class GradleRun {
    String id
    List execute = []
    int debugLevel
    String file
    String subDir
    boolean groovyScript = false
    Map envs = [:]
    String outputFile

    def withLoggingLevel(int debugLevel) {
        this.debugLevel = debugLevel
        this
    }
}