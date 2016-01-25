package com.smartbear.readyapi.client.execution;

import com.smartbear.readyapi.client.ExecutionListener;
import com.smartbear.readyapi.client.TestRecipe;
import io.swagger.client.auth.HttpBasicAuth;
import io.swagger.client.model.ProjectResultReport;
import io.swagger.client.model.ProjectResultReports;
import io.swagger.client.model.TestCase;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Responsible for executing test recipes on a Ready! API Server, synchronously or asynchronously.
 */
public class RecipeExecutor {

    private static final int NUMBER_OF_RETRIES_IN_CASE_OF_ERRORS = 3;
    private final SmartestApiWrapper apiStub;
    private HttpBasicAuth authentication;
    private final List<ExecutionListener> executionListeners = new CopyOnWriteArrayList<>();

    public RecipeExecutor(String host, int port, String basePath, SmartestApiWrapper apiStub) {
        this.apiStub = apiStub;
        apiStub.setBasePath("https://" + host + ":" + port + basePath);
    }

    public RecipeExecutor(String host, int port) {
        this(host, port, ServerDefaults.VERSION_PREFIX, new CodegenBasedSmartestApiWrapper());
    }

    public RecipeExecutor(String host) {
        this(host, ServerDefaults.DEFAULT_PORT);
    }

    public void setCredentials(String username, String password) {
        authentication = new HttpBasicAuth();
        authentication.setUsername(username);
        authentication.setPassword(password);
    }

    public void addExecutionListener(ExecutionListener listener) {
        executionListeners.add(listener);
    }

    public void removeExecutionListener(ExecutionListener listener) {
        executionListeners.remove(listener);
    }

    public Execution submitRecipe(TestRecipe recipe) {
        Execution execution = doExecuteTestCase(recipe.getTestCase(), true);
        if (execution != null) {
            for (ExecutionListener executionListener : executionListeners) {
                executionListener.requestSent(execution.getCurrentReport());
            }
            new ExecutionStatusChecker(execution).start();
        }
        return execution;
    }

    public Execution executeRecipe(TestRecipe recipe) {
        Execution execution = doExecuteTestCase(recipe.getTestCase(), false);
        if (execution != null) {
            notifyExecutionFinished(execution.getCurrentReport());
        }
        return execution;
    }

    public Execution cancelExecution(final Execution execution) {
        ProjectResultReport projectResultReport = apiStub.cancelExecution(execution.getId(), authentication);
        execution.addResultReport(projectResultReport);
        return execution;
    }

    public List<Execution> getExecutions() {
        List<Execution> executions = new ArrayList<>();
        ProjectResultReports projectResultReport = apiStub.getExecutions(authentication);
        for (ProjectResultReport resultReport : projectResultReport.getProjectResultReports()) {
            executions.add(new Execution(resultReport));
        }
        return executions;
    }

    private Execution doExecuteTestCase(TestCase testCase, boolean async) {
        try {
            ProjectResultReport projectResultReport = apiStub.postTestRecipe(testCase, async, authentication);
            return new Execution(projectResultReport);
        } catch (Exception e) {
            for (ExecutionListener executionListener : executionListeners) {
                executionListener.errorOccurred(e);
            }
            System.err.println("Error received when sending test recipe to server");
            e.printStackTrace();
        }
        return null;
    }

    private void notifyExecutionFinished(ProjectResultReport executionStatus) {
        for (ExecutionListener executionListener : executionListeners) {
            executionListener.executionFinished(executionStatus);
        }
    }

    private class ExecutionStatusChecker {
        private final Timer timer;

        private final Execution execution;

        private int errorCount = 0;

        public ExecutionStatusChecker(Execution execution) {
            this.execution = execution;
            timer = new Timer();
        }

        public void start() {
            timer.schedule(new CheckingExpireDateTask(), 0, 1000);
        }

        class CheckingExpireDateTask extends TimerTask {
            @Override
            public void run() {
                try {
                    ProjectResultReport executionStatus = apiStub.getExecutionStatus(execution.getId(), authentication);
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
                    e.printStackTrace();
                    errorCount++;
                }
            }
        }

    }
}