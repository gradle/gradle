#!/usr/bin/env ruby

if !ARGV.empty? && ARGV[0] == '-h'
  puts ("Usage: build-gradle [jc] | taskName]")
end

arg = ARGV.shift

# Skip unnecessary targets that take a really long time.
$skip = [
  'test', 'integTest',
  'checkstyleApi', 'checkstyleGroovy', 'checkstyleIntegTest',
  'checkstyleIntegTestGroovy', 'checkstyleJmh', 'checkstyleJmhGroovy',
  'checkstyleMain', 'checkstylePerformanceTest',
  'checkstylePerformanceTestGroovy', 'checkstyleSmokeTest',
  'checkstyleSmokeTestGroovy', 'checkstyleTest',
  'checkstyleTestFixtures', 'checkstyleTestFixturesGroovy',
  'checkstyleTestGroovy',
  'dslHtml', 'userguideHtml', 'userguidePdf', 'javadocAll', 'pdfUserguideXhtml',
  'userguideSingleHtml', 
]

$task = arg
case arg
when "j"
  $task = "languageJava"
when "c"
  $task = "core"
end

$args = "-x #{$skip.join(" -x ")}"

if $task
  cmd = "./gradlew #{$task}:build #{$args}"
else
  cmd = "./gradlew build #{$args}"
end

puts cmd
exec cmd
