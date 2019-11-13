package com.electriccloud.testsgen

import groovyx.net.http.HTTPBuilder
import groovyx.net.http.Method
import static groovyx.net.http.ContentType.JSON
import org.yaml.snakeyaml.Yaml

class GenerateTests {

    static def testRailUserName = "CHANGEME@electric-cloud.com"
    static def testRailPassword = "CHANGEME"
    static def yamlFile = "testConfig/testspec.yaml"
    static def defaultSpecFolder = "specs/src/test/groovy/com/cloudbees/plugin/spec"
    static def testFileTemplate = "/testFileTemplate"
    static def extraParametersInWhereTable = ['expectedOutcome', 'expectedSummary']

    static void main(String[] args){
        def parser = new Yaml()
        def testsStructure = parser.load((new File(yamlFile)).text)
//        def fileTemplateContent = getClass().getResource(testFileTemplate).text
//        def testFolderName = "${defaultSpecFolder}/" + (testsStructure['testsFolder'] ? testsStructure['testsFolder'] : '')
        def testFolderName = (testsStructure['testsFolder'] ? testsStructure['testsFolder'] : '')

        def testsFolder = new File("${defaultSpecFolder}/${testFolderName}")
        testsFolder.mkdir()

        if (testsStructure['testRail']){
            testRailUserName = testsStructure['testRail']['username']
            testRailPassword = testsStructure['testRail']['password']
        }

        for (def procedure in testsStructure['procedures']){
            generateFileWithTestsForProcedure(testFolderName, procedure, testsStructure)
        }

    }

    static def generateFileWithTestsForProcedure(def testFolderName, def procedure, def testsStructure){
        // separator / should be tests, not sure it is work for win or not
        def fileWithTests = new File("${defaultSpecFolder}/${testFolderName}/${procedure.key}.groovy")

        def className = procedure.key
        def parentClass = procedure.value['parentClass'] ?: testsStructure['parentClass']
        if (parentClass){
            className += " extends $parentClass "
        }

        def procedureName = procedure.value['procedureName'] ?: procedure.key
        def projectName = procedure.value['projectName'] ?: "test project: ${procedure.key}"

        def runParameters = procedure.value['parameters'] ?: []
        def mapOfRunParameters = generateMapOfRunParameters(runParameters)

        procedure.value['sharedParameters'] = procedure.value['sharedParameters'] ?: 'no'
        def sharedParameters = procedure.value['sharedParameters'].toBoolean()
        def sharedValues = generateDeclarationOfSharedVariables(sharedParameters, runParameters)

        def testCases
        if (procedure.value['testCases']['generate']) {
            def sectionId = procedure.value['testCases']['sectionId']
            def count = procedure.value['testCases']['count']
            testCases = createTestCases(sectionId, count, testRailUserName, testRailPassword)
        }
        else {
            testCases = procedure.value['testCases']['ids'] ?: []
        }
        def mapOfTestCases = generateTestsCases(testCases)

        def blocks = ''
        def possitiveBlock = generateBlockSpecs("$procedureName: Positive", runParameters, testCases)
        def negativeBlock = generateBlockSpecs("$procedureName: Negative", runParameters, testCases)
        blocks += possitiveBlock
        blocks += negativeBlock

        def content = getClass().getResource(testFileTemplate).text

        content = content.replace('TESTCLASSNAME', className)
        content = content.replace('PROCEDURENAME', procedureName)
        content = content.replace('PROJECTNAME', projectName)
        content = content.replace('SHARED_PARAMS', sharedValues)
        content = content.replace('RUNPARAMATERS', mapOfRunParameters)
        content = content.replace('TESTCASES', mapOfTestCases)
        content = content.replace('SPECBLOCKS', blocks)
        content = content.replace('FOLDER', ".${testFolderName}")
        fileWithTests.write content
    }

    static def generateMapOfRunParameters(def runParameters){
        def list = "\n"
        for (def runParameter in runParameters){
            list += "${' '*8}$runParameter  : '',\n"
        }
        def content = "runParameters = [$list${' '*4}]"
        return content
    }

    static def generateDeclarationOfSharedVariables(def generateSharedParameters, def parameters){
        def sharedBlock = ''
        parameters += extraParametersInWhereTable
        if (generateSharedParameters){
            for (def parameter in parameters){
                parameter = ( parameter == 'config' ? 'configName' : parameter)
                sharedBlock += "${" "*4}@Shared\n${" "*4}def ${parameter}\n"
            }
        }
        return sharedBlock
    }

    static def generateTestsCases(def testCasesIds){
        def list = "\n"
        for (def ids in testCasesIds){
            list += "${' '*8}C$ids  : [ids: 'C$ids', description: 'changeme'],\n"
        }
        def content = "TC = [$list${' '*4}]"
        return content

    }

    static def generateBlockSpecs(def blockName, def runParameters, def testCasesIds){
        def mapOfRunParameters = "\n"
        for (def runParameter in runParameters){
            def parameterValue  = (runParameter == 'config') ? 'configName' : runParameter
            mapOfRunParameters += "${' '*12}$runParameter  : $parameterValue,\n"
        }

        runParameters += extraParametersInWhereTable
        def whereParams = [] + 'caseId     ' + runParameters.collect { it == 'config' ? 'configName' : it}
        def whereTable = whereParams.join('  | ')
        for (def testCaseid in testCasesIds){
            def dataSetRaw = []
            whereParams.each {
                if (it.contains('caseId')){
                    dataSetRaw += "TC.C${testCaseid} "
                }
                else {
                    dataSetRaw += "''${' ' * (it.length() - 2)}"
                }
            }
            dataSetRaw = ' '*8 + dataSetRaw.join('  | ')
            whereTable += '\n' + dataSetRaw
        }
        def block = """
    @NewFeature(pluginVersion = "1.5.0")
    @Unroll
    def '$blockName #caseId.ids #caseId.description'() {
        given: "Tests parameters for procedure"
        def runParams = [$mapOfRunParameters${' '*8}]
        when: "Run procedure "
        def result = runProcedure(efProjectName, procedureName, runParameters, [], resName)
        def jobSummary = getStepSummary(result.jobId, procedureName)
        def outputParameters = getJobOutputParameters(result.jobId, 1)
        def jobProperties = getJobProperties(result.jobId)
        
        then: "Verify results"
        assert result.outcome == expectedOutcome
        assert jobSummary == expectedSummary
        where:
        $whereTable
        
    }
"""
        return block
    }

    static def createTestCases(def sectionId, def count, def userName, def password){
        def ids = []
        def SOCKET_TIMEOUT = 20 * 1000
        def CONNECTION_TIMEOUT = 5 * 1000

        def http = new HTTPBuilder("https://ecflow.testrail.net")
        http.ignoreSSLIssues()
        def postBody = ["title": "Update me", "type_id": 1, "priority_id": 3]
        for (def i=0; i<count; i++) {
            http.request(Method.POST, JSON) { req ->
                uri.path = "/index.php"
                uri.query = ["/api/v2/add_case/$sectionId": ""]
                body = postBody

                headers.'Authorization' =
                        "Basic ${"${userName}:${password}".bytes.encodeBase64().toString()}"

                req.getParams().setParameter("http.connection.timeout", CONNECTION_TIMEOUT)
                req.getParams().setParameter("http.socket.timeout", SOCKET_TIMEOUT)

                response.success = { resp, json ->
                    println "[DEBUG] Request  was successful ${resp.statusLine}, code: ${resp.status}: $json"
                    ids += json.id
                }

                response.failure = { resp ->
                    throw new Exception("[ERROR] Request failed with ${resp.statusLine}, code: ${resp.status}, ${resp}");
                }
            }
        }
        return ids
    }

}
