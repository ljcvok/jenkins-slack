#!/usr/bin/env groovy

import groovy.json.JsonOutput
import hudson.tasks.junit.CaseResult
import hudson.tasks.test.AbstractTestResultAction


def author = ""
def message = ""
def testSummary = ""
def total = 0
def failed = 0
def skipped = 0


static def getBuildColor(buildStatus) {
  if (buildStatus == "SUCCESS") {
    return "good"
  } else if (buildStatus == "UNSTABLE") {
    return "warning"
  }
  return "danger"

}

def getGitAuthor = {
  def commit = sh(returnStdout: true, script: 'git rev-parse HEAD')
  author = sh(returnStdout: true, script: "git --no-pager show -s --format='%an' ${commit}").trim()
}

def getLastCommitMessage = {
  message = sh(returnStdout: true, script: 'git log -1 --pretty=%B').trim()
}

@NonCPS
def getTestSummary = { ->
  def testResultAction = currentBuild.rawBuild.getAction(AbstractTestResultAction.class)
  def summary

  if (testResultAction != null) {
    total = testResultAction.getTotalCount()
    failed = testResultAction.getFailCount()
    skipped = testResultAction.getSkipCount()

    summary = "Passed: " + (total - failed - skipped)
    summary = summary + (", Failed: " + failed)
    summary = summary + (", Skipped: " + skipped)
  } else {
    summary = "No tests found"
  }
  return summary
}

@NonCPS
def getFailedTests = { ->
  def testResultAction = currentBuild.rawBuild.getAction(AbstractTestResultAction.class)
  def failedTestsString = "``` yaml\n"

  if (testResultAction != null) {
    def failedTests = testResultAction.getFailedTests()

    //only 50 tests to show
    if (failedTests.size() > 50) {
      failedTests = failedTests.subList(0, 49)
    }

    for (CaseResult cr : failedTests) {
      failedTestsString = failedTestsString + "${cr.getFullDisplayName()}:${cr.getErrorDetails() != null ? cr.getErrorDetails().take(20) : ''}\n"
    }
    failedTestsString = failedTestsString + "```"
  }
  return failedTestsString
}

def populateGlobalVariables = {
  getLastCommitMessage()
  getGitAuthor()
  testSummary = getTestSummary()
}

def call(slackURL) {
  populateGlobalVariables()
  def failedTestsString = getFailedTests()
  def jenkinsIcon = 'https://wiki.jenkins.io/download/attachments/2916393/headshot.png?version=1&modificationDate=1302753947000&api=v2'

  def payload = JsonOutput.toJson([text       : "",
                                   channel    : "builds",
                                   username   : "jenkins",
                                   icon_url   : jenkinsIcon,
                                   attachments: [
                                       [
                                           title      : "${env.JOB_NAME}, build #${env.BUILD_NUMBER}",
                                           title_link : "${env.RUN_DISPLAY_URL}",
                                           color      : "${getBuildColor(currentBuild.result)}",
                                           text       : "${currentBuild.result}\n${author}",
                                           "mrkdwn_in": ["fields"],
                                           fields     : [
                                               [
                                                   title: "Branch",
                                                   value: "${env.GIT_BRANCH}",
                                                   short: true
                                               ],
                                               [
                                                   title: "Test Results",
                                                   value: "${testSummary}",
                                                   short: true
                                               ],
                                               [
                                                   title: "Last Commit",
                                                   value: "<${env.RUN_CHANGES_DISPLAY_URL}|${message}>",
                                                   short: false
                                               ]
                                           ]
                                       ],
                                       failed > 0 ?
                                           [
                                               title      : "Failed Tests",
                                               color      : "${buildColor}",
                                               text       : "${failedTestsString}",
                                               "mrkdwn_in": ["text"],
                                           ] : null
                                   ]
  ])

  sh "curl -X POST --data-urlencode \'payload=${payload}\' ${slackURL}"
}




