package com.smartbear.readyapi4j.testserver.execution;

import com.smartbear.readyapi.client.model.ProjectResultReport;
import com.smartbear.readyapi.client.model.RequestTestStepBase;
import com.smartbear.readyapi.client.model.TestCase;
import com.smartbear.readyapi.client.model.TestCaseResultReport;
import com.smartbear.readyapi.client.model.TestStep;
import com.smartbear.readyapi.client.model.TestSuiteResultReport;
import com.smartbear.readyapi.client.model.UnresolvedFile;
import com.smartbear.readyapi4j.ExecutionListener;
import com.smartbear.readyapi4j.extractor.ExtractorData;
import com.smartbear.readyapi4j.extractor.ExtractorOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Base class for the various TestServer executors
 */

abstract class AbstractTestServerExecutor {
    private static Logger logger = LoggerFactory.getLogger(AbstractTestServerExecutor.class);
    private static final int NUMBER_OF_RETRIES_IN_CASE_OF_ERRORS = 3;
    private final List<ExecutionListener> executionListeners = new CopyOnWriteArrayList<>();
    protected List<ExtractorData> extractorDataList = new LinkedList<>();

    final TestServerClient testServerClient;

    AbstractTestServerExecutor(TestServerClient testServerClient) {
        this.testServerClient = testServerClient;
    }

    public void addExecutionListener(ExecutionListener listener) {
        executionListeners.add(listener);
    }

    public void removeExecutionListener(ExecutionListener listener) {
        executionListeners.remove(listener);
    }

    void notifyExecutionStarted(TestServerExecution execution) {
        if (execution != null) {
            for (ExecutionListener executionListener : executionListeners) {
                executionListener.executionStarted(execution.getCurrentReport());
            }
            new ExecutionStatusChecker(execution).start();
        }
    }

    void notifyErrorOccurred(Exception e) {
        for (ExecutionListener executionListener : executionListeners) {
            executionListener.errorOccurred(e);
        }
    }

    void notifyExecutionFinished(ProjectResultReport executionStatus) {
        List<String> extractorDataIdList = extractorDataList
                .stream()
                .map(ExtractorData::getExtractorDataId)
                .collect(Collectors.toList());
        TestCaseResultReport resultReport = executionStatus
                .getTestSuiteResultReports()
                .stream()
                .map(TestSuiteResultReport::getTestCaseResultReports)
                .flatMap(Collection::stream)
                .filter(testCaseResultReport ->
                        extractorDataIdList.contains(testCaseResultReport.getProperties().get(ExtractorData.EXTRACTOR_DATA_KEY)))
                .findAny()
                .orElse(null);

        if (resultReport != null) {
            Map<String, String> properties = resultReport.getProperties();
            runExtractors(properties);

            // After run, remove all unnecessary properties
            properties.entrySet().removeIf(entry -> entry.getKey().contains(properties.get(ExtractorData.EXTRACTOR_DATA_KEY)));
            properties.remove(ExtractorData.EXTRACTOR_DATA_KEY);
        }
        for (ExecutionListener executionListener : executionListeners) {
            executionListener.executionFinished(executionStatus);
        }
    }

    private void runExtractors(Map<String, String> properties) {
        ExtractorData extractorData = extractorDataList
                .stream()
                .filter(ed -> ed.getExtractorDataId().equals(properties.get(ExtractorData.EXTRACTOR_DATA_KEY)))
                .findAny()
                .orElse(null);
        if (extractorData != null) {
            properties.forEach((key, value) -> {
                ExtractorOperator operator = extractorData.getExtractorOperator(key);
                if (operator != null) {
                    operator.extractValue(value);
                }
            });
        }
    }

    void cancelExecutionAndThrowExceptionIfPendingDueToMissingClientCertificate(ProjectResultReport projectResultReport, TestCase testCase) {
        if (ProjectResultReport.StatusEnum.PENDING.equals(projectResultReport.getStatus())) {
            List<UnresolvedFile> unresolvedFiles = projectResultReport.getUnresolvedFiles();
            if (unresolvedFiles.size() > 0) {
                testServerClient.cancelExecution(projectResultReport.getExecutionID());
            }
            for (UnresolvedFile unresolvedFile : unresolvedFiles) {
                if (testCase == null || unresolvedFile.getFileName().equals(testCase.getClientCertFileName())) {
                    throw new ApiException(400, "Couldn't find client certificate file: " + unresolvedFile.getFileName());
                }
                throwExceptionIfTestStepCertificateIsUnresolved(testCase, unresolvedFile);
            }
        }
    }

    private void throwExceptionIfTestStepCertificateIsUnresolved(TestCase testCase, UnresolvedFile unresolvedFile) {
        for (TestStep testStep : testCase.getTestSteps()) {
            if (testStep instanceof RequestTestStepBase) {
                RequestTestStepBase requestTestStepBase = (RequestTestStepBase) testStep;
                if (unresolvedFile.getFileName().equals(requestTestStepBase.getClientCertificateFileName())) {
                    throw new ApiException(400, "Couldn't find test step client certificate file: " + requestTestStepBase.getClientCertificateFileName());
                }
            }
        }
    }

    private class ExecutionStatusChecker {
        private final Timer timer;

        private final TestServerExecution execution;

        private int errorCount = 0;

        ExecutionStatusChecker(TestServerExecution execution) {
            this.execution = execution;
            timer = new Timer();
        }

        void start() {
            timer.schedule(new CheckingExpireDateTask(), 0, 1000);
        }

        class CheckingExpireDateTask extends TimerTask {
            @Override
            public void run() {
                try {
                    ProjectResultReport executionStatus = testServerClient.getExecutionStatus(execution.getId());
                    execution.addResultReport(executionStatus);
                    if (!ProjectResultReport.StatusEnum.RUNNING.equals(executionStatus.getStatus())) {
                        notifyExecutionFinished(executionStatus);
                        timer.cancel();
                    }
                    errorCount = 0;
                } catch (Exception e) {
                    if (errorCount > NUMBER_OF_RETRIES_IN_CASE_OF_ERRORS) {
                        timer.cancel();
                    }
                    logger.debug("Error while checking for execution status", e);
                    errorCount++;
                }
            }
        }
    }
}
