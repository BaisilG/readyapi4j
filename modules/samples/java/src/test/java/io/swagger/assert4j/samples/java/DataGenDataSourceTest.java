package io.swagger.assert4j.samples.java;

import io.swagger.assert4j.result.RecipeExecutionResult;
import org.junit.Test;

import static io.swagger.assert4j.facade.execution.RecipeExecutionFacade.executeRecipe;
import static io.swagger.assert4j.support.AssertionUtils.assertExecutionResult;
import static io.swagger.assert4j.testserver.teststeps.ServerTestSteps.dataGenDataSource;
import static io.swagger.assert4j.testserver.teststeps.datasource.datagen.DataGenerators.*;
import static io.swagger.assert4j.teststeps.TestSteps.GET;
import static io.swagger.assert4j.teststeps.restrequest.ParameterBuilder.query;

public class DataGenDataSourceTest extends ApiTestBase {

    @Test
    public void createRecipeForAllDataGenerators() throws Exception {
        RecipeExecutionResult result = executeRecipe("DataGenerator Test",
                dataGenDataSource()
                        .withNumberOfRows(10)
                        .withProperties(
                                cityTypeProperty("cityProperty").duplicatedBy(2),
                                mac48ComputerAddressTypeProperty("computerAddressProperty"),
                                randomIntegerTypeProperty("integerProperty")
                        )
                        .named("DataSourceStep")
                        .withTestSteps(
                                GET("http://www.google.se/")
                                        .withParameters(
                                                query("a", "${DataSourceStep#cityProperty}"),
                                                query("b", "${DataSourceStep#computerAddressProperty}"),
                                                query("c", "${DataSourceStep#integerProperty}")
                                        )
                        )
        );

        assertExecutionResult(result);
    }
}
