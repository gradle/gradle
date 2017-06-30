#!/usr/bin/env ruby

path = File.expand_path "~/gradle-source-build"

cmd = "./gradlew install -Pgradle_installPath=#{path}"

puts cmd
`#{cmd}`

