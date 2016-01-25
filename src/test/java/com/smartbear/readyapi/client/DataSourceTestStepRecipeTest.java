package com.smartbear.readyapi.client;

import com.smartbear.readyapi.client.execution.ApiClientWrapper;
import com.smartbear.readyapi.client.execution.CodegenBasedSmartestApiWrapper;
import com.smartbear.readyapi.client.execution.Execution;
import com.smartbear.readyapi.client.execution.RecipeExecutor;
import com.smartbear.readyapi.client.execution.ServerDefaults;
import com.smartbear.readyapi.client.execution.SmartestApiWrapper;
import com.smartbear.readyapi.client.teststeps.TestStepTypes;
import com.sun.jersey.api.client.GenericType;
import io.swagger.client.ApiException;
import io.swagger.client.model.DataSource;
import io.swagger.client.model.DataSourceTestStep;
import io.swagger.client.model.ExcelDataSource;
import io.swagger.client.model.FileDataSource;
import io.swagger.client.model.ProjectResultReport;
import io.swagger.client.model.TestStep;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.File;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static com.smartbear.readyapi.client.TestRecipeBuilder.newTestRecipe;
import static com.smartbear.readyapi.client.teststeps.TestSteps.excelDataSource;
import static com.smartbear.readyapi.client.teststeps.TestSteps.fileDataSource;
import static com.smartbear.readyapi.client.teststeps.TestSteps.getRequest;
import static com.smartbear.readyapi.client.teststeps.TestSteps.gridDataSource;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.anyMap;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DataSourceTestStepRecipeTest {

    private static final String EXECUTIONS_BASE_PATH = ServerDefaults.SERVICE_BASE_PATH + "/executions";

    @Test
    public void buildsRecipeWithDataSourceTestStepWithGridDataSource() throws Exception {
        final List<String> propertyValues = Arrays.asList("Value1", "Value");
        TestRecipe recipe = newTestRecipe()
                .addStep(gridDataSource()
                        .addProperty("property1", propertyValues)
                        .addProperty("property2", propertyValues)
                )
                .buildTestRecipe();

        TestStep testStep = recipe.getTestCase().getTestSteps().get(0);
        assertThat(testStep.getType(), is(TestStepTypes.DATA_SOURCE.getName()));

        Map<String, List<String>> grid = ((DataSourceTestStep) testStep).getDataSource().getGrid();
        assertThat(grid.size(), is(2));
        assertThat(grid.get("property1"), is(propertyValues));
        assertThat(grid.get("property2"), is(propertyValues));
    }

    @Test
    public void buildsRecipeWithDataSourceTestStepWithExcelDataSource() throws Exception {
        TestRecipe recipe = newTestRecipe()
                .addStep(excelDataSource()
                        .addProperty("property1")
                        .addProperty("property2")
                        .withFilePath("ExcelFilePath")
                        .withWorksheet("Worksheet1")
                        .startAtCell("A1")
                        .ignoreEmpty()
                )
                .buildTestRecipe();

        DataSourceTestStep testStep = (DataSourceTestStep) recipe.getTestCase().getTestSteps().get(0);
        assertThat(testStep.getType(), is(TestStepTypes.DATA_SOURCE.getName()));

        DataSource dataSource = testStep.getDataSource();
        assertThat(dataSource.getProperties(), is(Arrays.asList("property1", "property2")));

        ExcelDataSource excel = dataSource.getExcel();
        assertThat(excel.getFile(), is("ExcelFilePath"));
        assertThat(excel.getWorksheet(), is("Worksheet1"));
        assertThat(excel.getStartAtCell(), is("A1"));
        assertThat(excel.getIgnoreEmpty(), is(true));
    }

    @Test
    public void buildsRecipeWithDataSourceTestStepWithFileDataSource() throws Exception {
        TestRecipe recipe = newTestRecipe()
                .addStep(fileDataSource()
                        .addProperty("property1")
                        .addProperty("property2")
                        .withFilePath("FilePath")
                        .withCharSet("UTF-8")
                        .withSeparator(",")
                        .quotedValues()
                        .trim()
                )
                .buildTestRecipe();

        DataSourceTestStep testStep = (DataSourceTestStep) recipe.getTestCase().getTestSteps().get(0);
        assertThat(testStep.getType(), is(TestStepTypes.DATA_SOURCE.getName()));

        DataSource dataSource = testStep.getDataSource();
        assertThat(dataSource.getProperties(), is(Arrays.asList("property1", "property2")));

        FileDataSource file = dataSource.getFile();
        assertThat(file.getFile(), is("FilePath"));
        assertThat(file.getCharset(), is("UTF-8"));
        assertThat(file.getSeparator(), is(","));
        assertThat(file.getQuotedValues(), is(true));
        assertThat(file.getTrim(), is(true));
    }

    @Test
    public void throwsExceptionIfDataSourceFileDoesNotExist() {
        TestRecipe recipe = newTestRecipe().addStep(
                excelDataSource()
                        .withFilePath("abc.xlsx")
                        .withWorksheet("Sheet1")
                        .addProperty("cityName")
                        .startAtCell("A1")
        )
                .buildTestRecipe();

        RecipeExecutor recipeExecutor = new RecipeExecutor("localhost", ServerDefaults.DEFAULT_PORT);
        recipeExecutor.addExecutionListener(new ExecutionListener() {
            @Override
            public void requestSent(ProjectResultReport projectResultReport) {

            }

            @Override
            public void executionFinished(ProjectResultReport projectResultReport) {

            }

            @Override
            public void errorOccurred(Exception exception) {
                assertThat(exception.getMessage(), is("Data source file not found: abc.xlsx"));
            }
        });
        recipeExecutor.submitRecipe(recipe);
    }

    @Test
    public void sendsRecipeWithExcelDataSource() throws Exception {
        final String dataSourceFilePath = getClass().getResource("/DataSource.xlsx").getPath();

        TestRecipe recipe = newTestRecipe().
                addStep(
                        excelDataSource()
                                .withFilePath(dataSourceFilePath)
                                .withWorksheet("Sheet1")
                                .addProperty("cityName")
                                .startAtCell("A1"))
                .addStep(
                        getRequest("http://maps.googleapis.com/maps/api/geocode/xml")
                                .addQueryParameter("address", "${DataSourceStep#cityName}")
                )
                .buildTestRecipe();

        ApiClientWrapper apiClientWrapper = mockApiClientWrapper();
        SmartestApiWrapper smartestApiWrapper = new CodegenBasedSmartestApiWrapper(apiClientWrapper);
        RecipeExecutor recipeExecutor = new RecipeExecutor("localhost", ServerDefaults.DEFAULT_PORT,
                ServerDefaults.VERSION_PREFIX, smartestApiWrapper);
        recipeExecutor.setCredentials("user", "password");
        Execution execution = recipeExecutor.executeRecipe(recipe);

        verify(apiClientWrapper, times(1)).invokeAPI(eq(EXECUTIONS_BASE_PATH), anyString(), anyList(), anyObject(),
                anyMap(), anyString(), eq("application/json"), any(String[].class), any(GenericType.class));

        ArgumentCaptor<Map> formDataArgumentCaptor = ArgumentCaptor.forClass(Map.class);
        verify(apiClientWrapper, times(1)).invokeAPI(eq(EXECUTIONS_BASE_PATH + "/" + execution.getId() + "/files"),
                anyString(), anyList(), anyObject(), formDataArgumentCaptor.capture(), anyString(),
                eq("multipart/form-data"), any(String[].class), any(GenericType.class));

        Iterator iterator = formDataArgumentCaptor.getValue().entrySet().iterator();
        Map.Entry<String, File> dataSourceFile = (Map.Entry<String, File>) iterator.next();
        assertThat(dataSourceFile.getKey(), is("DataSource.xlsx"));
        assertThat(dataSourceFile.getValue(), is(new File(dataSourceFilePath)));
    }

    private ApiClientWrapper mockApiClientWrapper() throws ApiException {
        ApiClientWrapper apiClientWrapper = mock(ApiClientWrapper.class);
        when(apiClientWrapper.parameterToPairs(anyString(), anyString(), anyObject())).thenCallRealMethod();
        when(apiClientWrapper.escapeString(anyString())).thenCallRealMethod();
        when(apiClientWrapper.serialize(anyObject(), anyString())).thenCallRealMethod();
        ProjectResultReport projectResultReport = new ProjectResultReport();
        projectResultReport.setExecutionID("exec_id");
        projectResultReport.setStatus(ProjectResultReport.StatusEnum.RUNNING);
        when(apiClientWrapper.invokeAPI(anyString(), anyString(), anyList(), anyObject(), anyMap(), anyString(), anyString(), any(String[].class), any(GenericType.class))).thenReturn(projectResultReport);

        return apiClientWrapper;
    }
}