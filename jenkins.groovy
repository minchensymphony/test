import groovy.io.FileType
import groovy.xml.XmlSlurper

def createFiles() {
    for (int i = 0; i < 5; i++) {
        def file = new File("c9-loadtest-${i}-test-results.xml")

        file.write('''<?xml version="1.0" encoding="utf-8"?>
<testsuites>
    <testsuite name="pytest" errors="0" failures="1" skipped="0" tests="1" time="349.739" timestamp="2024-04-25T00:12:56.616650" hostname="C9LPF2QBHN4">
        <testcase classname="run_e2e_test" name="test_trader_login" time="348.031">
            <failure message="AssertionError: Could not sign user 'minchen1' into C9Trader application.  Audio engine not loaded."></failure>
        </testcase>
    </testsuite>
</testsuites>''')
    }
}

def readResults(String dir) {
    def failureCount = 0

    new File(dir).traverse(type: FileType.FILES, nameFilter: ~/.*test-results.xml$/) {
        def xml = new XmlSlurper().parseText(it.getText("UTF-8"))

        for (testsuite in xml.testsuite) {
            for (testcase in testsuite.testcase) {
                if (testsuite.@failures.text().toInteger() > 0) {
                    for (failure in testcase.failure) {
                        failureCount++
                        println "Test failed: ${it.name}, message: ${failure.@message}"
                    }
                }
            }
        }
    }

    return failureCount
}

createFiles()

def failureCount = readResults('.')

if (failureCount > 0) {
    print "There are ${failureCount + (failureCount == 1 ? " test" : " tests")} failed."
    System.exit(-1)
}