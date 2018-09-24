#!/usr/bin/env groovy

import groovy.json.JsonOutput
import hudson.tasks.junit.CaseResult
import hudson.tasks.test.AbstractTestResultAction

def slackNotificationChannel = "builds"
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

def notifySlack(slackUrl, text, channel, attachments) {
  def jenkinsIcon = 'https://wiki.jenkins.io/download/attachments/2916393/headshot.png?version=1&modificationDate=1302753947000&api=v2'

  def payload = JsonOutput.toJson([text       : text,
                                   channel    : channel,
                                   username   : "jenkins",
                                   icon_url   : jenkinsIcon,
                                   attachments: attachments
  ])

  sh "curl -X POST --data-urlencode \'payload=${payload}\' ${slackURL}"
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



