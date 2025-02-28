/*
 * Copyright 2021 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kie.kogito.index;

import java.io.IOException;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.JsonNode;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;

import static io.restassured.RestAssured.given;
import static java.util.Collections.singletonMap;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public abstract class AbstractProcessDataIndexIT {

    private static Duration TIMEOUT = Duration.ofSeconds(30);

    static {
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    RequestSpecification spec;

    public abstract String getDataIndexURL();

    public boolean validateDomainData() {
        return true;
    }

    public boolean checkRuntimeConnectionsResponses() {
        return true;
    }

    public RequestSpecification dataIndexSpec() {
        if (spec == null) {
            spec = new RequestSpecBuilder().setBaseUri(getDataIndexURL()).build();
        }
        return spec;
    }

    @Test
    public void testProcessInstanceEvents() throws IOException {
        String pId = given()
                .contentType(ContentType.JSON)
                .body("{\"traveller\" : {\"firstName\" : \"Darth\",\"lastName\" : \"Vader\",\"email\" : \"darth.vader@deathstar.com\",\"nationality\" : \"Tatooine\"}}")
                .when()
                .post("/approvals")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .extract()
                .path("id");

        String flTaskId = given()
                .contentType(ContentType.JSON)
                .queryParam("user", "admin")
                .queryParam("group", "managers")
                .pathParam("processId", pId)
                .when()
                .get("/approvals/{processId}/tasks")
                .then()
                .statusCode(200)
                .body("$.size", is(1))
                .body("[0].name", is("firstLineApproval"))
                .body("[0].id", notNullValue())
                .extract()
                .path("[0].id");

        if (validateDomainData()) {
            await()
                    .atMost(TIMEOUT)
                    .untilAsserted(() -> given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                            .body("{ \"query\" : \"{Approvals{ id, traveller { firstName, lastName }, metadata { processInstances { id, state }, userTasks { id, name, state } } } }\" }")
                            .when().post("/graphql")
                            .then().statusCode(200)
                            .body("data.Approvals.size()", is(1))
                            .body("data.Approvals[0].id", is(pId))
                            .body("data.Approvals[0].traveller.firstName", is("Darth"))
                            .body("data.Approvals[0].traveller.lastName", is("Vader"))
                            .body("data.Approvals[0].metadata.processInstances", is(notNullValue()))
                            .body("data.Approvals[0].metadata.processInstances.size()", is(1))
                            .body("data.Approvals[0].metadata.processInstances[0].id", is(pId))
                            .body("data.Approvals[0].metadata.processInstances[0].state", is("ACTIVE"))
                            .body("data.Approvals[0].metadata.userTasks", is(notNullValue()))
                            .body("data.Approvals[0].metadata.userTasks.size()", is(1))
                            .body("data.Approvals[0].metadata.userTasks[0].id", is(flTaskId))
                            .body("data.Approvals[0].metadata.userTasks[0].name", is("firstLineApproval"))
                            .body("data.Approvals[0].metadata.userTasks[0].state", is("Ready")));
        }

        await()
                .atMost(TIMEOUT)
                .untilAsserted(() -> given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                        .body("{ \"query\" : \"{ProcessInstances{ id, processId, state } }\" }")
                        .when().post("/graphql")
                        .then().statusCode(200)
                        .body("data.ProcessInstances.size()", is(1))
                        .body("data.ProcessInstances[0].id", is(pId))
                        .body("data.ProcessInstances[0].processId", is("approvals"))
                        .body("data.ProcessInstances[0].state", is("ACTIVE")));

        await()
                .atMost(TIMEOUT)
                .untilAsserted(() -> given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                        .body("{ \"query\" : \"{UserTaskInstances{ id, name, state } }\" }")
                        .when().post("/graphql")
                        .then().statusCode(200)
                        .body("data.UserTaskInstances.size()", is(1))
                        .body("data.UserTaskInstances[0].id", is(flTaskId))
                        .body("data.UserTaskInstances[0].name", is("firstLineApproval"))
                        .body("data.UserTaskInstances[0].state", is("Ready")));

        await()
                .atMost(TIMEOUT)
                .untilAsserted(() -> given().contentType(ContentType.JSON)
                        .when()
                        .queryParam("user", "admin")
                        .queryParam("group", "managers")
                        .pathParam("processId", pId)
                        .pathParam("taskId", flTaskId)
                        .body(singletonMap("approved", true))
                        .post("/approvals/{processId}/firstLineApproval/{taskId}")
                        .then()
                        .statusCode(200)
                        .body("firstLineApproval", is(true)));

        String slTaskId = given()
                .contentType(ContentType.JSON)
                .queryParam("user", "manager")
                .queryParam("group", "managers")
                .pathParam("processId", pId)
                .when()
                .get("/approvals/{processId}/tasks")
                .then()
                .statusCode(200)
                .body("$.size", is(1))
                .body("[0].name", is("secondLineApproval"))
                .body("[0].id", notNullValue())
                .extract()
                .path("[0].id");

        await()
                .atMost(TIMEOUT)
                .untilAsserted(() -> given().contentType(ContentType.JSON)
                        .when()
                        .queryParam("user", "manager")
                        .queryParam("group", "managers")
                        .pathParam("processId", pId)
                        .pathParam("taskId", slTaskId)
                        .body(singletonMap("approved", true))
                        .post("/approvals/{processId}/secondLineApproval/{taskId}")
                        .then()
                        .statusCode(200)
                        .body("secondLineApproval", is(true)));

        await()
                .atMost(TIMEOUT)
                .untilAsserted(() -> given()
                        .contentType(ContentType.JSON)
                        .when()
                        .pathParam("processId", pId)
                        .get("/approvals/{processId}")
                        .then()
                        .statusCode(404));

        await()
                .atMost(TIMEOUT)
                .untilAsserted(() -> given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                        .body("{ \"query\" : \"{ProcessInstances{ id, processId, state } }\" }")
                        .when().post("/graphql")
                        .then().statusCode(200)
                        .body("data.ProcessInstances.size()", is(1))
                        .body("data.ProcessInstances[0].id", is(pId))
                        .body("data.ProcessInstances[0].processId", is("approvals"))
                        .body("data.ProcessInstances[0].state", is("COMPLETED")));

        await()
                .atMost(TIMEOUT)
                .untilAsserted(() -> given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                        .body("{ \"query\" : \"{UserTaskInstances{ id, name, state } }\" }")
                        .when().post("/graphql")
                        .then().statusCode(200)
                        .body("data.UserTaskInstances.size()", is(2)));

        if (validateDomainData()) {
            await()
                    .atMost(TIMEOUT)
                    .untilAsserted(() -> given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                            .body("{ \"query\" : \"{Approvals{ id, firstLineApproval, secondLineApproval, metadata { processInstances { id, state }, userTasks { id, name, state } } } }\" }")
                            .when().post("/graphql")
                            .then().statusCode(200)
                            .body("data.Approvals.size()", is(1))
                            .body("data.Approvals[0].id", is(pId))
                            .body("data.Approvals[0].firstLineApproval", is(true))
                            .body("data.Approvals[0].secondLineApproval", is(true))
                            .body("data.Approvals[0].metadata.processInstances", is(notNullValue()))
                            .body("data.Approvals[0].metadata.processInstances.size()", is(1))
                            .body("data.Approvals[0].metadata.processInstances[0].id", is(pId))
                            .body("data.Approvals[0].metadata.processInstances[0].state", is("COMPLETED"))
                            .body("data.Approvals[0].metadata.userTasks", is(notNullValue()))
                            .body("data.Approvals[0].metadata.userTasks.size()", is(2)));
        }

        String pId2 = createTestProcessInstance();
        await()
                .atMost(TIMEOUT)
                .untilAsserted(() -> getProcessInstanceById(pId2, "ACTIVE"));
        if (checkRuntimeConnectionsResponses()) {
            await()
                    .atMost(TIMEOUT)
                    .untilAsserted(() -> given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                            .body("{ \"query\" : \"{ ProcessInstances (where: { id: {equal: \\\"" + pId2 + "\\\"}}) { nodeDefinitions {id} nodes {id}} }\"}")
                            .when().post("/graphql")
                            .then()
                            .statusCode(200)
                            .body("data.ProcessInstances[0].nodeDefinitions", notNullValue())
                            .body("data.ProcessInstances[0].nodeDefinitions.size()", is(4))
                            .body("data.ProcessInstances[0].nodes.size()", is(2)));

            final String taskId = given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                    .body("{ \"query\" : \"{ UserTaskInstances (where: { processInstanceId: {equal: \\\"" + pId2 + "\\\"}}) { id description potentialGroups } }\"}")
                    .when().post("/graphql")
                    .then()
                    .statusCode(200)
                    .body("data.UserTaskInstances[0].description", nullValue())
                    .body("data.UserTaskInstances[0].potentialGroups[0]", equalTo("managers"))
                    .extract().path("data.UserTaskInstances[0].id");

            String taskSchema = given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                    .body("{ \"query\" : \"{ UserTaskInstances (where: {id: {equal:\\\"" + taskId + "\\\" }}){ " +
                            "schema ( user: \\\"manager\\\", groups: [\\\"managers\\\", \\\"users\\\", \\\"IT\\\"] )" +
                            "}}\"}")
                    .when().post("/graphql")
                    .then()
                    .statusCode(200)
                    .extract().path("data.UserTaskInstances[0].schema");
            checkExpectedTaskSchema(taskSchema);

            await()
                    .atMost(TIMEOUT)
                    .untilAsserted(() -> given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                            .body("{ \"query\" : \"mutation{ TaskPartialUpdate(" +
                                    "id: \\\"" + pId2 + "\\\", " +
                                    "taskId: \\\"" + taskId + "\\\", " +
                                    "user: \\\"manager\\\", " +
                                    "groups: [\\\"managers\\\", \\\"users\\\", \\\"IT\\\"], " +
                                    "taskInfo: { description: \\\"NewDescription\\\", priority: \\\"low\\\"} " +
                                    ")}\"}")
                            .when().post("/graphql")
                            .then()
                            .statusCode(200)
                            .body("errors", nullValue()));

            given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                    .body("{ \"query\" : \"{ UserTaskInstances (where: { processInstanceId: {equal: \\\"" + pId2 + "\\\"}}) { id description priority potentialGroups} }\"}")
                    .when().post("/graphql")
                    .then()
                    .statusCode(200)
                    .body("data.UserTaskInstances[0].description", equalTo("NewDescription"))
                    .body("data.UserTaskInstances[0].priority", equalTo("low"))
                    .body("data.UserTaskInstances[0].potentialGroups[0]", equalTo("managers"));

            await()
                    .atMost(TIMEOUT)
                    .untilAsserted(() -> given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                            .body("{ \"query\" : \"mutation{ TaskUpdate(" +
                                    "id: \\\"" + pId2 + "\\\", " +
                                    "taskId: \\\"" + taskId + "\\\", " +
                                    "user: \\\"manager\\\", " +
                                    "groups: [\\\"managers\\\", \\\"users\\\", \\\"IT\\\"], " +
                                    "taskInfo: { description: \\\"NewDescription2\\\"} " +
                                    ")}\"}")
                            .when().post("/graphql")
                            .then()
                            .statusCode(200)
                            .body("errors", nullValue()));

            given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                    .body("{ \"query\" : \"{ UserTaskInstances (where: { processInstanceId: {equal: \\\"" + pId2 + "\\\"}}) { id description priority potentialGroups} }\"}")
                    .when().post("/graphql")
                    .then()
                    .statusCode(200)
                    .body("data.UserTaskInstances[0].description", equalTo("NewDescription2"))
                    .body("data.UserTaskInstances[0].priority", nullValue());

            String vars = given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                    .body("{ \"query\" : \"{ ProcessInstances (where: { id: {equal: \\\"" + pId2 + "\\\"}}) { variables} }\"}")
                    .when().post("/graphql")
                    .then()
                    .statusCode(200).extract().path("data.ProcessInstances[0].variables");
            if (vars != null) {
                await()
                        .atMost(TIMEOUT)
                        .untilAsserted(() -> given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                                .body("{ \"query\" : \"mutation{ ProcessInstanceUpdateVariables(id:\\\"" + pId2 + "\\\", variables:\\\"" +
                                        vars.replace("Darth", "Anakin")
                                                .replace("\"", "\\\\\\\"")
                                        + "\\\")}\"}")
                                .when().post("/graphql")
                                .then()
                                .statusCode(200)
                                .body("errors", nullValue()));
                await()
                        .atMost(TIMEOUT)
                        .untilAsserted(() -> given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                                .body("{ \"query\" : \"{ ProcessInstances (where: { id: {equal: \\\"" + pId2 + "\\\"}}) { variables} }\"}")
                                .when().post("/graphql")
                                .then()
                                .statusCode(200)
                                .body("data.ProcessInstances[0].variables", containsString("Anakin")));
            }
            given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                    .body("{ \"query\" : \"mutation{ NodeInstanceTrigger(id:\\\"" + pId2 + "\\\", nodeId:\\\"_8B62D3CA-5D03-4B2B-832B-126469288BB4\\\")}\"}")
                    .when().post("/graphql")
                    .then()
                    .statusCode(200)
                    .body("errors", nullValue());

            given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                    .body("{ \"query\" : \"mutation{ NodeInstanceRetrigger(id:\\\"" + pId2 + "\\\", nodeInstanceId:\\\"_8B62D3CA-5D03-4B2B-832B-126469288BB4\\\")}\"}")
                    .when().post("/graphql")
                    .then()
                    .statusCode(200)
                    .body("errors[0].message", containsString("FAILED: Retrigger NodeInstance"));

            given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                    .body("{ \"query\" : \"mutation{ NodeInstanceCancel(id:\\\"" + pId2 + "\\\", nodeInstanceId:\\\"_8B62D3CA-5D03-4B2B-832B-126469288BB4\\\")}\"}")
                    .when().post("/graphql")
                    .then()
                    .statusCode(200)
                    .body("errors[0].message", notNullValue());

            await()
                    .atMost(TIMEOUT).untilAsserted(() -> given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                            .body("{ \"query\" : \"mutation {ProcessInstanceAbort( id: \\\"" + pId2 + "\\\")}\"}")
                            .when().post("/graphql")
                            .then()
                            .statusCode(200)
                            .body("errors", nullValue()));
            await()
                    .atMost(TIMEOUT)
                    .untilAsserted(() -> getProcessInstanceById(pId2, "ABORTED"));

            given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                    .body("{ \"query\" : \"mutation{ ProcessInstanceRetry( id: \\\"" + pId2 + "\\\")}\"}")
                    .when().post("/graphql")
                    .then()
                    .statusCode(200)
                    .body("data.ProcessInstanceRetry", nullValue());

            given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                    .body("{ \"query\" : \"mutation{ ProcessInstanceSkip( id: \\\"" + pId2 + "\\\")}\"}")
                    .when().post("/graphql")
                    .then()
                    .statusCode(200)
                    .body("errors[0].message", containsString("FAILED: SKIP"));

            given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                    .body("{ \"query\" : \"mutation{ UndefinedMutation( id: \\\"" + pId2 + "\\\")}\"}")
                    .when().post("/graphql")
                    .then()
                    .statusCode(200)
                    .body("errors[0].message", containsString("Field 'UndefinedMutation' in type 'Mutation' is undefined"));
        } else {
            given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                    .body("{ \"query\" : \"mutation{ ProcessInstanceUpdateVariables(id:\\\"" + pId2 + "\\\", variables:\\\"{}\\\")}\"}")
                    .when().post("/graphql")
                    .then()
                    .statusCode(200);

            given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                    .body("{ \"query\" : \"mutation{ NodeInstanceTrigger(id:\\\"" + pId2 + "\\\", nodeId:\\\"_8B62D3CA-5D03-4B2B-832B-126469288BB4\\\")}\"}")
                    .when().post("/graphql")
                    .then()
                    .statusCode(200);

            given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                    .body("{ \"query\" : \"mutation{ NodeInstanceRetrigger(id:\\\"" + pId2 + "\\\", nodeInstanceId:\\\"_8B62D3CA-5D03-4B2B-832B-126469288BB4\\\")}\"}")
                    .when().post("/graphql")
                    .then()
                    .statusCode(200);

            given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                    .body("{ \"query\" : \"mutation{ NodeInstanceCancel(id:\\\"" + pId2 + "\\\", nodeInstanceId:\\\"_8B62D3CA-5D03-4B2B-832B-126469288BB4\\\")}\"}")
                    .when().post("/graphql")
                    .then()
                    .statusCode(200);

            given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                    .body("{ \"query\" : \"mutation {ProcessInstanceAbort( id: \\\"" + pId2 + "\\\")}\"}")
                    .when().post("/graphql")
                    .then()
                    .statusCode(200);

            given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                    .body("{ \"query\" : \"mutation{ ProcessInstanceRetry( id: \\\"" + pId2 + "\\\")}\"}")
                    .when().post("/graphql")
                    .then()
                    .statusCode(200);

            given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                    .body("{ \"query\" : \"mutation{ ProcessInstanceSkip( id: \\\"" + pId2 + "\\\")}\"}")
                    .when().post("/graphql")
                    .then()
                    .statusCode(200);

            given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                    .body("{ \"query\" : \"mutation{ UndefinedMutation( id: \\\"" + pId2 + "\\\")}\"}")
                    .when().post("/graphql")
                    .then()
                    .statusCode(200)
                    .body("errors[0].message", containsString("Field 'UndefinedMutation' in type 'Mutation' is undefined"));
        }
    }

    protected String createTestProcessInstance() {
        return given()
                .contentType(ContentType.JSON)
                .body("{\"traveller\" : {\"firstName\" : \"Darth\",\"lastName\" : \"Vader\",\"email\" : \"darth.vader@deathstar.com\",\"nationality\" : \"Tatooine\"}}")
                .when()
                .post("/approvals")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .extract()
                .path("id");
    }

    protected ValidatableResponse getProcessInstanceById(String processInstanceId, String state) {
        return given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                .body("{ \"query\" : \"{ProcessInstances(where: {  id: {  equal : \\\"" + processInstanceId + "\\\"}}){ id, processId, state } }\" }")
                .when().post("/graphql")
                .then().statusCode(200)
                .body("data.ProcessInstances.size()", is(1))
                .body("data.ProcessInstances[0].id", is(processInstanceId))
                .body("data.ProcessInstances[0].processId", is("approvals"))
                .body("data.ProcessInstances[0].state", is(state));
    }

    private void checkExpectedTaskSchema(String taskSchema) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode schemaJsonNode = mapper.readTree(taskSchema);
        assertEquals("\"object\"", schemaJsonNode.at("/type").toString());

        assertEquals(4, schemaJsonNode.at("/phases").size());
        assertTrue(schemaJsonNode.get("phases").toString().contains("abort"));
        assertTrue(schemaJsonNode.get("phases").toString().contains("claim"));
        assertTrue(schemaJsonNode.get("phases").toString().contains("skip"));
        assertTrue(schemaJsonNode.get("phases").toString().contains("complete"));

        assertEquals(2, schemaJsonNode.at("/properties").size());

        assertEquals("true", schemaJsonNode.at("/properties/approved/output").toString());
        assertEquals("\"boolean\"", schemaJsonNode.at("/properties/approved/type").toString());

        assertEquals("\"object\"", schemaJsonNode.at("/properties/traveller/type").toString());
        assertEquals("true", schemaJsonNode.at("/properties/traveller/input").toString());
        assertEquals(6, schemaJsonNode.at("/properties/traveller/properties").size());
        assertEquals("\"object\"", schemaJsonNode.at("/properties/traveller/properties/address/type").toString());
        assertEquals(4, schemaJsonNode.at("/properties/traveller/properties/address/properties").size());
        assertEquals("\"string\"",
                schemaJsonNode.at("/properties/traveller/properties/address/properties/city/type").toString());
        assertEquals("\"string\"",
                schemaJsonNode.at("/properties/traveller/properties/address/properties/country/type").toString());
        assertEquals("\"string\"",
                schemaJsonNode.at("/properties/traveller/properties/address/properties/street/type").toString());
        assertEquals("\"string\"",
                schemaJsonNode.at("/properties/traveller/properties/address/properties/zipCode/type").toString());
        assertEquals("\"string\"",
                schemaJsonNode.at("/properties/traveller/properties/email/type").toString());
        assertEquals("\"string\"",
                schemaJsonNode.at("/properties/traveller/properties/firstName/type").toString());
        assertEquals("\"string\"",
                schemaJsonNode.at("/properties/traveller/properties/lastName/type").toString());
        assertEquals("\"string\"",
                schemaJsonNode.at("/properties/traveller/properties/nationality/type").toString());
        assertEquals("\"boolean\"",
                schemaJsonNode.at("/properties/traveller/properties/processed/type").toString());
    }
}
