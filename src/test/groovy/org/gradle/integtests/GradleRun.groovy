package org.gradle.integtests

class GradleRun {
    String id
    List execute = []
    int debugLevel
    String file
    String subDir
    boolean groovyScript
    Map envs = [:]
    String outputFile
    boolean expectFailure

    def withLoggingLevel(int debugLevel) {
        this.debugLevel = debugLevel
        this
    }
}