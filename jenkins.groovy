import groovy.cli.commons.CliBuilder
import groovy.io.FileType
import groovy.json.JsonOutput
import groovy.xml.XmlSlurper

import java.util.stream.Collectors

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
    def failures = new HashMap()

    new File(dir).traverse(type: FileType.FILES, nameFilter: ~/.*test-results.xml$/) {
        def xml = new XmlSlurper().parseText(it.getText("UTF-8"))

        for (testsuite in xml.testsuite) {
            for (testcase in testsuite.testcase) {
                if (testsuite.@failures.text().toInteger() > 0) {
                    for (failure in testcase.failure) {
                        failures.put(it.name, failure.@message.text())
                    }
                }
            }
        }
    }

    return failures
}

def postSuccess(String buildUrl) {
    postStatus("""\
            <messageML>
                <div>
                    <h2>C9Trader Login Load Test</h2>
                    <card class='barStyle' accent='tempo-bg-color--green' iconSrc='https://www.jenkins.io/images/logos/actor/actor.png'>
                        <header>
                            <div>
                                <a class='tempo-text-color--link' href='${buildUrl}'>All Tests Passed</a>
                                <span class='tempo-text-color--normal'> - </span>
                                <span class='tempo-text-color--normal'>Process Time (UTC) : ${new Date().format("yyyy-MM-dd HH:mm:ss", TimeZone.getTimeZone('UTC'))}</span>
                            </div>
                        </header>
                    </card>
                </div>
            </messageML>
        """)
}

def postFailed(String buildUrl, Map<String, String> failures) {
    postStatus("""\
            <messageML>
                <div>
                    <h2>C9Trader Login Load Test</h2>
                    <card class='barStyle' accent='tempo-bg-color--red' iconSrc='https://www.jenkins.io/images/logos/fire/fire.png'>
                        <header>
                            <div>
                                <a class='tempo-text-color--link' href='${buildUrl}'>Test Failed</a>
                                <span class='tempo-text-color--normal'> - </span>
                                <span class='tempo-text-color--normal'>Process Time (UTC) : ${new Date().format("yyyy-MM-dd HH:mm:ss", TimeZone.getTimeZone('UTC'))}</span>
                            </div>
                        </header>
                        <body>
                            <div>
                                ${failures.entrySet().stream().map { return """\
                                <div>
                                    <span class='tempo-text-color--secondary'>${it.getKey()}: </span>
                                    <span class='tempo-text-color--normal'>${it.getValue()}</span>
                                </div>""" }.collect(Collectors.joining(''))}
                            </div>
                        </body>
                    </card>
                </div>
            </messageML>
        """)
}

def postStatus(String message) {
    try {
        String postUrl = "https://corporate.symphony.com/universal-webhook/552f195a-9a60-489b-87f6-8bf19745c6bc/6e94c37a-26fd-4fd3-8372-56e50477dfd4/6e76b7c3-0a0f-4765-90c4-9e2043f2baef/53384a28-e4d8-4671-948c-363d118d4494"
        String body = JsonOutput.toJson(["message": message
                .stripIndent()
                .replace("\t", "    ")
                .replace("    ", "")
                .replace("\n", "")])

        println("${postUrl}: ${body}")

        def connection = new URL(postUrl).openConnection()

        connection.setRequestMethod('POST')
        connection.setRequestProperty('x-tag', "c9trader-login-loadtest")
        connection.setRequestProperty('Content-Type', 'application/json')
        connection.setDoOutput(true)
        connection.getOutputStream().write(body.getBytes('UTF-8'))

        println("Updating Status. ResponseCode: ${connection.responseCode}")

        connection.disconnect()
    } catch (error) {
        println(error)
    }
}

CliBuilder cli = new CliBuilder(usage: 'jenkins.groovy -l').tap {
    l longOpt: 'build-url', type: String, required: true, "Set Jenkins build URL"
}
def options = cli.parse(args)

if (!options) {
    System.exit(-1)
}

createFiles()

Map<String, String> failures = readResults('.')

if (!failures.isEmpty()) {
    failures.forEach { k, v -> println "Test failed in ${k}: ${v}" }
    println "There are ${failures.size() + (failures.size() == 1 ? " test" : " tests")} failed."
    postFailed(options.'build-url', failures)
    System.exit(-1)
} else {
    println "All tests passed."
    postSuccess(options.'build-url')
}