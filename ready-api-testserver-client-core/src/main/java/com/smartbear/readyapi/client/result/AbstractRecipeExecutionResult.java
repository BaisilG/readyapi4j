package com.smartbear.readyapi.client.result;

import com.google.common.collect.Lists;
import com.smartbear.readyapi.client.model.ProjectResultReport;
import com.smartbear.readyapi.client.model.TestCaseResultReport;
import com.smartbear.readyapi.client.model.TestStepResultReport;
import com.smartbear.readyapi.client.model.TestSuiteResultReport;

import java.util.Collections;
import java.util.List;

public abstract class AbstractRecipeExecutionResult implements RecipeExecutionResult {
    protected final ProjectResultReport report;
    protected final List<TestStepResult> results = Lists.newArrayList();

    public AbstractRecipeExecutionResult(ProjectResultReport currentReport, TestStepResultBuilder testStepResultBuilder) {
        report = currentReport;

        for (TestSuiteResultReport testSuiteReport : report.getTestSuiteResultReports()) {
            for (TestCaseResultReport testCaseResultReport : testSuiteReport.getTestCaseResultReports()) {
                for (TestStepResultReport testStepResultReport : testCaseResultReport.getTestStepResultReports()) {
                    results.add(testStepResultBuilder.buildTestStepResult(testStepResultReport));
                }
            }
        }
    }

    @Override
    public long getTimeTaken() {
        return report.getTimeTaken();
    }

    @Override
    public String getExecutionId() {
        return report.getExecutionID();
    }

    @Override
    public ProjectResultReport.StatusEnum getStatus() {
        return report.getStatus();
    }

    @Override
    public int getResultCount() {
        return results.size();
    }

    public List<String> getErrorMessages() {
        List<String> result = Lists.newArrayList();

        for (TestStepResult testStepResultReport : results) {
            if (testStepResultReport.getAssertionStatus() == TestStepResultReport.AssertionStatusEnum.FAILED) {
                result.addAll(testStepResultReport.getMessages());
            }
        }

        return result;
    }

    @Override
    public TestStepResult getFirstTestStepResult(String name) {
        for (TestStepResult testStepResultReport : results) {
            if (testStepResultReport.getTestStepName().equalsIgnoreCase(name)) {
                return testStepResultReport;
            }
        }

        return null;
    }

    @Override
    public TestStepResult getLastTestStepResult(String testStepName) {
        for (TestStepResult testStepResultReport : Lists.reverse(results)) {
            if (testStepResultReport.getTestStepName().equalsIgnoreCase(testStepName)) {
                return testStepResultReport;
            }
        }

        return null;
    }

    @Override
    public List<TestStepResult> getTestStepResults() {
        return Collections.unmodifiableList(results);
    }

    @Override
    public List<TestStepResult> getFailedTestStepsResults() {
        List<TestStepResult> result = Lists.newArrayList();

        for (TestStepResult testStepResultReport : results) {
            if (testStepResultReport.getAssertionStatus() == TestStepResultReport.AssertionStatusEnum.FAILED) {
                result.add(testStepResultReport);
            }
        }

        return result;
    }

    @Override
    public List<TestStepResult> getFailedTestStepsResults(String testStepName) {
        List<TestStepResult> result = Lists.newArrayList();

        for (TestStepResult testStepResultReport : results) {
            if (testStepResultReport.getAssertionStatus() == TestStepResultReport.AssertionStatusEnum.FAILED &&
                    testStepResultReport.getTestStepName().equalsIgnoreCase(testStepName)) {
                result.add(testStepResultReport);
            }
        }

        return result;
    }

    @Override
    public List<TestStepResult> getTestStepResults(String testStepName) {
        List<TestStepResult> result = Lists.newArrayList();

        for (TestStepResult testStepResultReport : results) {
            if (testStepResultReport.getTestStepName().equalsIgnoreCase(testStepName)) {
                result.add(testStepResultReport);
            }
        }

        return result;
    }

    @Override
    public TestStepResult getTestStepResult(int index) {
        return results.get(index);
    }

    public interface TestStepResultBuilder {
        TestStepResult buildTestStepResult(TestStepResultReport testStepResultReport);
    }
}
