/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates.
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
package org.kie.kogito.index.service;

import java.net.URI;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import javax.inject.Inject;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.kie.kogito.index.event.KogitoProcessCloudEvent;
import org.kie.kogito.index.event.KogitoUserTaskCloudEvent;
import org.kie.kogito.persistence.protobuf.ProtobufService;

import com.fasterxml.jackson.databind.node.ObjectNode;

import io.restassured.http.ContentType;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.isA;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.kie.kogito.index.GraphQLUtils.getDealsByTaskId;
import static org.kie.kogito.index.GraphQLUtils.getDealsByTaskIdNoActualOwner;
import static org.kie.kogito.index.GraphQLUtils.getProcessInstanceById;
import static org.kie.kogito.index.GraphQLUtils.getProcessInstanceByIdAndErrorNode;
import static org.kie.kogito.index.GraphQLUtils.getProcessInstanceByIdAndState;
import static org.kie.kogito.index.GraphQLUtils.getProcessInstanceByParentProcessInstanceId;
import static org.kie.kogito.index.GraphQLUtils.getTravelsByProcessInstanceId;
import static org.kie.kogito.index.GraphQLUtils.getTravelsByUserTaskId;
import static org.kie.kogito.index.GraphQLUtils.getUserTaskInstanceById;
import static org.kie.kogito.index.GraphQLUtils.getUserTaskInstanceByIdAndActualOwner;
import static org.kie.kogito.index.GraphQLUtils.getUserTaskInstanceByIdNoActualOwner;
import static org.kie.kogito.index.TestUtils.getProcessCloudEvent;
import static org.kie.kogito.index.TestUtils.getUserTaskCloudEvent;
import static org.kie.kogito.index.json.JsonUtils.getObjectMapper;
import static org.kie.kogito.index.model.ProcessInstanceState.ACTIVE;
import static org.kie.kogito.index.model.ProcessInstanceState.COMPLETED;
import static org.kie.kogito.index.model.ProcessInstanceState.ERROR;

public abstract class AbstractDomainIndexingServiceIT extends AbstractIndexingServiceIT {

    @Inject
    public ProtobufService protobufService;

    @AfterEach
    void tearDown() {
        super.tearDown();
        if (cacheService.getDomainModelCache("travels") != null) {
            cacheService.getDomainModelCache("travels").clear();
        }
        if (cacheService.getDomainModelCache("deals") != null) {
            cacheService.getDomainModelCache("deals").clear();
        }
    }

    @Test
    void testAddBrokenProtoFile() {
        try {
            protobufService.registerProtoBufferType(getBrokenProtoBufferFile());
            fail("Registering broken proto file should fail");
        } catch (Exception ex) {
            assertThat(ex.getMessage())
                    .isEqualTo("Failed to resolve type of field \"org.demo.travels.traveller\". Type not found : stringa");
        }
    }

    @Test
    void testAddProtoFileMissingModel() {
        try {
            protobufService.registerProtoBufferType(getProtoBufferFileWithoutModel());
            fail("Registering broken proto file should fail");
        } catch (Exception ex) {
            assertThat(ex.getMessage()).isEqualTo(
                    "Missing marker for main message type in proto file, please add option kogito_model=\"messagename\"");
        }
    }

    @Test
    void testAddProtoFileMissingId() {
        try {
            protobufService.registerProtoBufferType(getProtoBufferFileWithoutId());
            fail("Registering broken proto file should fail");
        } catch (Exception ex) {
            assertThat(ex.getMessage())
                    .isEqualTo("Missing marker for process id in proto file, please add option kogito_id=\"processid\"");
        }
    }

    @Test
    void testAddProtoFileMissingModelType() {
        try {
            protobufService.registerProtoBufferType(getProtoBufferFileWithoutModelType());
            fail("Registering broken proto file should fail");
        } catch (Exception ex) {
            assertThat(ex.getMessage()).isEqualTo(
                    "Could not find message with name: org.demo.traveller in proto file, e, please review option kogito_model");
        }
    }

    @Test //Reproducer for KOGITO-172
    void testAddProtoFileTwice() throws Exception {
        protobufService.registerProtoBufferType(getProtoBufferFileV1());
        given().contentType(ContentType.JSON)
                .body("{ \"query\" : \"{Game{ player, id, name, metadata { processInstances { id } } } }\" }")
                .when().post("/graphql")
                .then().log().ifValidationFails().statusCode(200).body("data.Game", isA(Collection.class));
        given().contentType(ContentType.JSON)
                .body("{ \"query\" : \"{ProcessInstances{ id, processId, rootProcessId, rootProcessInstanceId, parentProcessInstanceId } }\" }")
                .when().post("/graphql")
                .then().log().ifValidationFails().statusCode(200).body("data.ProcessInstances", isA(Collection.class));

        protobufService.registerProtoBufferType(getProtoBufferFileV2());
        given().contentType(ContentType.JSON)
                .body("{ \"query\" : \"{Game{ id, name, company, metadata { processInstances { id } } } }\" }")
                .when().post("/graphql")
                .then().log().ifValidationFails().statusCode(200).body("data.Game", isA(Collection.class));
        given().contentType(ContentType.JSON)
                .body("{ \"query\" : \"{ProcessInstances{ id, processId, rootProcessId, rootProcessInstanceId, parentProcessInstanceId } }\" }")
                .when().post("/graphql")
                .then().log().ifValidationFails().statusCode(200).body("data.ProcessInstances", isA(Collection.class));
    }

    @Test
    void testAddProtoFile() throws Exception {
        String processId = "travels";
        String subProcessId = processId + "_sub";
        String processInstanceId = UUID.randomUUID().toString();
        String subProcessInstanceId = UUID.randomUUID().toString();
        String firstTaskId = UUID.randomUUID().toString();
        String secondTaskId = UUID.randomUUID().toString();
        String state = "InProgress";

        protobufService.registerProtoBufferType(getProcessProtobufFileContent());

        given().contentType(ContentType.JSON).body("{ \"query\" : \"{Travels{ id } }\" }")
                .when().post("/graphql")
                .then().log().ifValidationFails().statusCode(200).body("data.Travels", isA(Collection.class));

        KogitoProcessCloudEvent startEvent = getProcessCloudEvent(processId, processInstanceId, ACTIVE, null, null, null);
        indexProcessCloudEvent(startEvent);

        validateProcessInstance(getProcessInstanceByIdAndState(processInstanceId, ACTIVE), startEvent);

        given().contentType(ContentType.JSON)
                .body(getTravelsByProcessInstanceId(processInstanceId))
                .when().post("/graphql")
                .then().log().ifValidationFails().statusCode(200)
                .body("data.Travels[0].id", is(processInstanceId))
                .body("data.Travels[0].__typename", is("Travels"))
                .body("data.Travels[0].metadata.lastUpdate",
                        is(formatZonedDateTime(startEvent.getTime().withZoneSameInstant(ZoneOffset.UTC))))
                .body("data.Travels[0].metadata.processInstances.size()", is(1))
                .body("data.Travels[0].metadata.processInstances[0].id", is(processInstanceId))
                .body("data.Travels[0].metadata.processInstances[0].processId", is(processId))
                .body("data.Travels[0].metadata.processInstances[0].processName", is(startEvent.getData().getProcessName()))
                .body("data.Travels[0].metadata.processInstances[0].rootProcessId", is(nullValue()))
                .body("data.Travels[0].metadata.processInstances[0].rootProcessInstanceId", is(nullValue()))
                .body("data.Travels[0].metadata.processInstances[0].parentProcessInstanceId", is(nullValue()))
                .body("data.Travels[0].metadata.processInstances[0].start",
                        is(formatZonedDateTime(startEvent.getData().getStart().withZoneSameInstant(ZoneOffset.UTC))))
                .body("data.Travels[0].metadata.processInstances[0].end", is(nullValue()))
                .body("data.Travels[0].metadata.processInstances[0].lastUpdate",
                        is(formatZonedDateTime(startEvent.getData().getLastUpdate().withZoneSameInstant(ZoneOffset.UTC))))
                .body("data.Travels[0].metadata.processInstances[0].endpoint", is(startEvent.getSource().toString()))
                .body("data.Travels[0].metadata.processInstances[0].serviceUrl", is("http://localhost:8080"))
                .body("data.Travels[0].traveller.firstName", is("Maciej"))
                .body("data.Travels[0].hotel.name", is("Meriton"))
                .body("data.Travels[0].flight.flightNumber", is("MX555"));

        KogitoProcessCloudEvent subProcessStartEvent = getProcessCloudEvent(subProcessId, subProcessInstanceId, ACTIVE,
                processInstanceId, processId, processInstanceId);
        subProcessStartEvent.getData().setVariables((ObjectNode) getObjectMapper().readTree(
                "{ \"traveller\":{\"firstName\":\"Maciej\", \"email\":\"mail@mail.com\", \"nationality\":\"Polish\"} }"));
        subProcessStartEvent.setSource(URI.create("/" + subProcessId));
        indexProcessCloudEvent(subProcessStartEvent);

        validateProcessInstance(getProcessInstanceByIdAndState(subProcessInstanceId, ACTIVE), subProcessStartEvent);
        validateProcessInstance(getProcessInstanceByIdAndState(processInstanceId, ACTIVE), startEvent, subProcessInstanceId);

        given().contentType(ContentType.JSON)
                .body(getTravelsByProcessInstanceId(subProcessInstanceId))
                .when().post("/graphql")
                .then().log().ifValidationFails().statusCode(200)
                .body("data.Travels[0].id", is(processInstanceId))
                .body("data.Travels[0].__typename", is("Travels"))
                .body("data.Travels[0].metadata.lastUpdate",
                        is(formatZonedDateTime(subProcessStartEvent.getTime().withZoneSameInstant(ZoneOffset.UTC))))
                .body("data.Travels[0].metadata.processInstances.size()", is(2))
                .body("data.Travels[0].metadata.processInstances[0].id", is(processInstanceId))
                .body("data.Travels[0].metadata.processInstances[0].processId", is(processId))
                .body("data.Travels[0].metadata.processInstances[0].processName", is(startEvent.getData().getProcessName()))
                .body("data.Travels[0].metadata.processInstances[0].rootProcessId", is(nullValue()))
                .body("data.Travels[0].metadata.processInstances[0].rootProcessInstanceId", is(nullValue()))
                .body("data.Travels[0].metadata.processInstances[0].parentProcessInstanceId", is(nullValue()))
                .body("data.Travels[0].metadata.processInstances[0].start",
                        is(formatZonedDateTime(startEvent.getData().getStart().withZoneSameInstant(ZoneOffset.UTC))))
                .body("data.Travels[0].metadata.processInstances[0].end", is(nullValue()))
                .body("data.Travels[0].metadata.processInstances[0].lastUpdate",
                        is(formatZonedDateTime(startEvent.getData().getLastUpdate().withZoneSameInstant(ZoneOffset.UTC))))
                .body("data.Travels[0].metadata.processInstances[0].endpoint", is(startEvent.getSource().toString()))
                .body("data.Travels[0].metadata.processInstances[0].serviceUrl", is("http://localhost:8080"))
                .body("data.Travels[0].metadata.processInstances[1].id", is(subProcessInstanceId))
                .body("data.Travels[0].metadata.processInstances[1].processId", is(subProcessId))
                .body("data.Travels[0].metadata.processInstances[1].processName",
                        is(subProcessStartEvent.getData().getProcessName()))
                .body("data.Travels[0].metadata.processInstances[1].rootProcessId", is(processId))
                .body("data.Travels[0].metadata.processInstances[1].rootProcessInstanceId", is(processInstanceId))
                .body("data.Travels[0].metadata.processInstances[1].parentProcessInstanceId", is(processInstanceId))
                .body("data.Travels[0].metadata.processInstances[1].start",
                        is(formatZonedDateTime(subProcessStartEvent.getData().getStart().withZoneSameInstant(ZoneOffset.UTC))))
                .body("data.Travels[0].metadata.processInstances[1].end", is(nullValue()))
                .body("data.Travels[0].traveller.firstName", is("Maciej"))
                .body("data.Travels[0].traveller.email", is("mail@mail.com"))
                .body("data.Travels[0].traveller.nationality", is("Polish"))
                .body("data.Travels[0].hotel.name", is("Meriton"))
                .body("data.Travels[0].flight.flightNumber", is("MX555"))
                .body("data.Travels[0].flight.arrival", is("2019-08-20T22:12:57.340Z"))
                .body("data.Travels[0].flight.departure", is("2019-08-20T07:12:57.340Z"));

        KogitoProcessCloudEvent endEvent = getProcessCloudEvent(processId, processInstanceId, COMPLETED, null, null, null);
        indexProcessCloudEvent(endEvent);

        validateProcessInstance(getProcessInstanceByIdAndState(processInstanceId, COMPLETED), endEvent, subProcessInstanceId);

        KogitoUserTaskCloudEvent firstUserTaskEvent = getUserTaskCloudEvent(firstTaskId, subProcessId, subProcessInstanceId,
                processInstanceId, processId, state);

        indexUserTaskCloudEvent(firstUserTaskEvent);

        validateUserTaskInstance(getUserTaskInstanceById(firstTaskId), firstUserTaskEvent);

        given().contentType(ContentType.JSON)
                .body(getTravelsByUserTaskId(firstTaskId))
                .when().post("/graphql")
                .then().log().ifValidationFails().statusCode(200)
                .body("data.Travels[0].id", is(processInstanceId))
                .body("data.Travels[0].__typename", is("Travels"))
                .body("data.Travels[0].metadata.lastUpdate",
                        is(formatZonedDateTime(firstUserTaskEvent.getTime().withZoneSameInstant(ZoneOffset.UTC))))
                .body("data.Travels[0].metadata.userTasks.size()", is(1))
                .body("data.Travels[0].metadata.userTasks[0].id", is(firstTaskId))
                .body("data.Travels[0].metadata.userTasks[0].processInstanceId", is(subProcessInstanceId))
                .body("data.Travels[0].metadata.userTasks[0].description", is("TaskDescription"))
                .body("data.Travels[0].metadata.userTasks[0].name", is("TaskName"))
                .body("data.Travels[0].metadata.userTasks[0].priority", is("High"))
                .body("data.Travels[0].metadata.userTasks[0].actualOwner", is("kogito"))
                .body("data.Travels[0].metadata.userTasks[0].lastUpdate",
                        is(formatZonedDateTime(firstUserTaskEvent.getTime().withZoneSameInstant(ZoneOffset.UTC))))
                .body("data.Travels[0].metadata.processInstances.size()", is(2))
                .body("data.Travels[0].metadata.processInstances[0].id", is(processInstanceId))
                .body("data.Travels[0].metadata.processInstances[0].processId", is(processId))
                .body("data.Travels[0].metadata.processInstances[0].processName", is(endEvent.getData().getProcessName()))
                .body("data.Travels[0].metadata.processInstances[0].rootProcessId", is(nullValue()))
                .body("data.Travels[0].metadata.processInstances[0].rootProcessInstanceId", is(nullValue()))
                .body("data.Travels[0].metadata.processInstances[0].parentProcessInstanceId", is(nullValue()))
                .body("data.Travels[0].metadata.processInstances[0].start",
                        is(formatZonedDateTime(endEvent.getData().getStart().withZoneSameInstant(ZoneOffset.UTC))))
                .body("data.Travels[0].metadata.processInstances[0].end",
                        is(formatZonedDateTime(endEvent.getData().getEnd().withZoneSameInstant(ZoneOffset.UTC))))
                .body("data.Travels[0].metadata.processInstances[0].lastUpdate",
                        is(formatZonedDateTime(endEvent.getData().getLastUpdate().withZoneSameInstant(ZoneOffset.UTC))))
                .body("data.Travels[0].metadata.processInstances[1].id", is(subProcessInstanceId))
                .body("data.Travels[0].metadata.processInstances[1].processId", is(subProcessId))
                .body("data.Travels[0].metadata.processInstances[1].processName",
                        is(subProcessStartEvent.getData().getProcessName()))
                .body("data.Travels[0].metadata.processInstances[1].rootProcessId", is(processId))
                .body("data.Travels[0].metadata.processInstances[1].rootProcessInstanceId", is(processInstanceId))
                .body("data.Travels[0].metadata.processInstances[1].parentProcessInstanceId", is(processInstanceId))
                .body("data.Travels[0].metadata.processInstances[1].start",
                        is(formatZonedDateTime(subProcessStartEvent.getData().getStart().withZoneSameInstant(ZoneOffset.UTC))))
                .body("data.Travels[0].metadata.processInstances[1].end", is(nullValue()))
                .body("data.Travels[0].metadata.processInstances[1].endpoint", is(subProcessStartEvent.getSource().toString()))
                .body("data.Travels[0].metadata.processInstances[1].serviceUrl", is(nullValue()))
                .body("data.Travels[0].metadata.processInstances[1].lastUpdate", is(formatZonedDateTime(
                        subProcessStartEvent.getData().getLastUpdate().withZoneSameInstant(ZoneOffset.UTC))))
                .body("data.Travels[0].traveller.firstName", is("Maciej"))
                .body("data.Travels[0].hotel.name", is("Meriton"))
                .body("data.Travels[0].flight.flightNumber", is("MX555"))
                .body("data.Travels[0].flight.arrival", is("2019-08-20T22:12:57.340Z"))
                .body("data.Travels[0].flight.departure", is("2019-08-20T07:12:57.340Z"));

        KogitoUserTaskCloudEvent secondUserTaskEvent = getUserTaskCloudEvent(secondTaskId, processId, processInstanceId, null,
                null, state);

        indexUserTaskCloudEvent(secondUserTaskEvent);

        validateUserTaskInstance(getUserTaskInstanceById(secondTaskId), secondUserTaskEvent);

        given().contentType(ContentType.JSON)
                .body(getTravelsByUserTaskId(secondTaskId))
                .when().post("/graphql")
                .then().log().ifValidationFails().statusCode(200)
                .body("data.Travels[0].id", is(processInstanceId))
                .body("data.Travels[0].__typename", is("Travels"))
                .body("data.Travels[0].metadata.lastUpdate",
                        is(formatZonedDateTime(secondUserTaskEvent.getTime().withZoneSameInstant(ZoneOffset.UTC))))
                .body("data.Travels[0].metadata.userTasks.size()", is(2))
                .body("data.Travels[0].metadata.userTasks[0].id", is(firstTaskId))
                .body("data.Travels[0].metadata.userTasks[0].processInstanceId", is(subProcessInstanceId))
                .body("data.Travels[0].metadata.userTasks[0].description", is("TaskDescription"))
                .body("data.Travels[0].metadata.userTasks[0].name", is("TaskName"))
                .body("data.Travels[0].metadata.userTasks[0].priority", is("High"))
                .body("data.Travels[0].metadata.userTasks[0].actualOwner", is("kogito"))
                .body("data.Travels[0].metadata.userTasks[0].lastUpdate", is(formatZonedDateTime(
                        firstUserTaskEvent.getData().getLastUpdate().withZoneSameInstant(ZoneOffset.UTC))))
                .body("data.Travels[0].metadata.userTasks[1].id", is(secondTaskId))
                .body("data.Travels[0].metadata.userTasks[1].processInstanceId", is(processInstanceId))
                .body("data.Travels[0].metadata.userTasks[1].description", is("TaskDescription"))
                .body("data.Travels[0].metadata.userTasks[1].name", is("TaskName"))
                .body("data.Travels[0].metadata.userTasks[1].priority", is("High"))
                .body("data.Travels[0].metadata.userTasks[1].actualOwner", is("kogito"))
                .body("data.Travels[0].metadata.userTasks[1].lastUpdate", is(formatZonedDateTime(
                        secondUserTaskEvent.getData().getLastUpdate().withZoneSameInstant(ZoneOffset.UTC))))
                .body("data.Travels[0].metadata.processInstances.size()", is(2))
                .body("data.Travels[0].metadata.processInstances[0].id", is(processInstanceId))
                .body("data.Travels[0].metadata.processInstances[0].processId", is(processId))
                .body("data.Travels[0].metadata.processInstances[0].processName", is(endEvent.getData().getProcessName()))
                .body("data.Travels[0].metadata.processInstances[0].rootProcessId", is(nullValue()))
                .body("data.Travels[0].metadata.processInstances[0].rootProcessInstanceId", is(nullValue()))
                .body("data.Travels[0].metadata.processInstances[0].parentProcessInstanceId", is(nullValue()))
                .body("data.Travels[0].metadata.processInstances[0].start",
                        is(formatZonedDateTime(endEvent.getData().getStart().withZoneSameInstant(ZoneOffset.UTC))))
                .body("data.Travels[0].metadata.processInstances[0].end",
                        is(formatZonedDateTime(endEvent.getData().getEnd().withZoneSameInstant(ZoneOffset.UTC))))
                .body("data.Travels[0].metadata.processInstances[0].lastUpdate",
                        is(formatZonedDateTime(endEvent.getData().getLastUpdate().withZoneSameInstant(ZoneOffset.UTC))))
                .body("data.Travels[0].metadata.processInstances[0].endpoint", is(endEvent.getSource().toString()))
                .body("data.Travels[0].metadata.processInstances[0].serviceUrl", is("http://localhost:8080"))
                .body("data.Travels[0].metadata.processInstances[1].id", is(subProcessInstanceId))
                .body("data.Travels[0].metadata.processInstances[1].processId", is(subProcessId))
                .body("data.Travels[0].metadata.processInstances[1].processName",
                        is(subProcessStartEvent.getData().getProcessName()))
                .body("data.Travels[0].metadata.processInstances[1].rootProcessId", is(processId))
                .body("data.Travels[0].metadata.processInstances[1].rootProcessInstanceId", is(processInstanceId))
                .body("data.Travels[0].metadata.processInstances[1].parentProcessInstanceId", is(processInstanceId))
                .body("data.Travels[0].metadata.processInstances[1].start",
                        is(formatZonedDateTime(subProcessStartEvent.getData().getStart().withZoneSameInstant(ZoneOffset.UTC))))
                .body("data.Travels[0].metadata.processInstances[1].lastUpdate", is(formatZonedDateTime(
                        subProcessStartEvent.getData().getLastUpdate().withZoneSameInstant(ZoneOffset.UTC))))
                .body("data.Travels[0].metadata.processInstances[1].end", is(nullValue()))
                .body("data.Travels[0].metadata.processInstances[1].endpoint", is(subProcessStartEvent.getSource().toString()))
                .body("data.Travels[0].metadata.processInstances[1].serviceUrl", is(nullValue()))
                .body("data.Travels[0].traveller.firstName", is("Maciej"))
                .body("data.Travels[0].hotel.name", is("Meriton"))
                .body("data.Travels[0].flight.flightNumber", is("MX555"))
                .body("data.Travels[0].flight.arrival", is("2019-08-20T22:12:57.340Z"))
                .body("data.Travels[0].flight.departure", is("2019-08-20T07:12:57.340Z"));
    }

    @Test
    void testIndexingDomainUsingUserTaskEventFirst() throws Exception {
        String taskId = UUID.randomUUID().toString();
        String state = "InProgress";
        String processId = "travels";
        String processInstanceId = UUID.randomUUID().toString();

        protobufService.registerProtoBufferType(getProcessProtobufFileContent());

        given().contentType(ContentType.JSON).body("{ \"query\" : \"{ Travels{ id } }\" }")
                .when().post("/graphql")
                .then().log().ifValidationFails().statusCode(200).body("data.Travels", isA(Collection.class));

        KogitoUserTaskCloudEvent userTaskEvent = getUserTaskCloudEvent(taskId, processId, processInstanceId, null, null, state);
        indexUserTaskCloudEvent(userTaskEvent);

        given().contentType(ContentType.JSON)
                .body(getTravelsByUserTaskId(taskId))
                .when().post("/graphql")
                .then().log().ifValidationFails().statusCode(200)
                .body("data.Travels[0].id", is(processInstanceId))
                .body("data.Travels[0].__typename", is("Travels"))
                .body("data.Travels[0].flight", is(nullValue()))
                .body("data.Travels[0].hotel", is(nullValue()))
                .body("data.Travels[0].traveller", is(nullValue()))
                .body("data.Travels[0].metadata.lastUpdate",
                        is(formatZonedDateTime(userTaskEvent.getTime().withZoneSameInstant(ZoneOffset.UTC))))
                .body("data.Travels[0].metadata.userTasks.size()", is(1))
                .body("data.Travels[0].metadata.userTasks[0].id", is(taskId))
                .body("data.Travels[0].metadata.userTasks[0].processInstanceId", is(processInstanceId))
                .body("data.Travels[0].metadata.userTasks[0].description", is(userTaskEvent.getData().getDescription()))
                .body("data.Travels[0].metadata.userTasks[0].name", is(userTaskEvent.getData().getName()))
                .body("data.Travels[0].metadata.userTasks[0].priority", is(userTaskEvent.getData().getPriority()))
                .body("data.Travels[0].metadata.userTasks[0].actualOwner", is(userTaskEvent.getData().getActualOwner()))
                .body("data.Travels[0].metadata.userTasks[0].lastUpdate",
                        is(formatZonedDateTime(userTaskEvent.getTime().withZoneSameInstant(ZoneOffset.UTC))))
                .body("data.Travels[0].metadata.processInstances", is(nullValue()));

        KogitoProcessCloudEvent processEvent = getProcessCloudEvent(processId, processInstanceId, ACTIVE, null, null, null);
        indexProcessCloudEvent(processEvent);

        given().contentType(ContentType.JSON)
                .body(getTravelsByProcessInstanceId(processInstanceId))
                .when().post("/graphql")
                .then().log().ifValidationFails().statusCode(200)
                .body("data.Travels[0].id", is(processInstanceId))
                .body("data.Travels[0].__typename", is("Travels"))
                .body("data.Travels[0].flight.flightNumber", is("MX555"))
                .body("data.Travels[0].hotel.name", is("Meriton"))
                .body("data.Travels[0].traveller.firstName", is("Maciej"))
                .body("data.Travels[0].metadata.lastUpdate",
                        is(formatZonedDateTime(processEvent.getTime().withZoneSameInstant(ZoneOffset.UTC))))
                .body("data.Travels[0].metadata.userTasks.size()", is(1))
                .body("data.Travels[0].metadata.userTasks[0].id", is(taskId))
                .body("data.Travels[0].metadata.userTasks[0].processInstanceId", is(processInstanceId))
                .body("data.Travels[0].metadata.userTasks[0].description", is(userTaskEvent.getData().getDescription()))
                .body("data.Travels[0].metadata.userTasks[0].name", is(userTaskEvent.getData().getName()))
                .body("data.Travels[0].metadata.userTasks[0].priority", is(userTaskEvent.getData().getPriority()))
                .body("data.Travels[0].metadata.userTasks[0].actualOwner", is(userTaskEvent.getData().getActualOwner()))
                .body("data.Travels[0].metadata.userTasks[0].lastUpdate",
                        is(formatZonedDateTime(userTaskEvent.getTime().withZoneSameInstant(ZoneOffset.UTC))))
                .body("data.Travels[0].metadata.processInstances.size()", is(1))
                .body("data.Travels[0].metadata.processInstances[0].id", is(processInstanceId))
                .body("data.Travels[0].metadata.processInstances[0].processId", is(processId))
                .body("data.Travels[0].metadata.processInstances[0].processName", is(processEvent.getData().getProcessName()))
                .body("data.Travels[0].metadata.processInstances[0].rootProcessId", is(nullValue()))
                .body("data.Travels[0].metadata.processInstances[0].rootProcessInstanceId", is(nullValue()))
                .body("data.Travels[0].metadata.processInstances[0].parentProcessInstanceId", is(nullValue()))
                .body("data.Travels[0].metadata.processInstances[0].lastUpdate",
                        is(formatZonedDateTime(processEvent.getTime().withZoneSameInstant(ZoneOffset.UTC))))
                .body("data.Travels[0].metadata.processInstances[0].endpoint", is(processEvent.getSource().toString()))
                .body("data.Travels[0].metadata.processInstances[0].serviceUrl", is("http://localhost:8080"));
    }

    @Test
    void testIndexingDomainUsingProcessEventFirst() throws Exception {
        String taskId = UUID.randomUUID().toString();
        String state = "InProgress";
        String processId = "travels";
        String processInstanceId = UUID.randomUUID().toString();

        protobufService.registerProtoBufferType(getProcessProtobufFileContent());

        given().contentType(ContentType.JSON).body("{ \"query\" : \"{ Travels{ id } }\" }")
                .when().post("/graphql")
                .then().log().ifValidationFails().statusCode(200).body("data.Travels", isA(Collection.class));

        KogitoProcessCloudEvent processEvent = getProcessCloudEvent(processId, processInstanceId, ACTIVE, null, null, null);
        indexProcessCloudEvent(processEvent);

        given().contentType(ContentType.JSON)
                .body(getTravelsByProcessInstanceId(processInstanceId))
                .when().post("/graphql")
                .then().log().ifValidationFails().statusCode(200)
                .body("data.Travels[0].id", is(processInstanceId))
                .body("data.Travels[0].flight.flightNumber", is("MX555"))
                .body("data.Travels[0].hotel.name", is("Meriton"))
                .body("data.Travels[0].traveller.firstName", is("Maciej"))
                .body("data.Travels[0].metadata.lastUpdate",
                        is(formatZonedDateTime(processEvent.getTime().withZoneSameInstant(ZoneOffset.UTC))))
                .body("data.Travels[0].metadata.userTasks", is(nullValue()))
                .body("data.Travels[0].metadata.processInstances.size()", is(1))
                .body("data.Travels[0].metadata.processInstances[0].id", is(processInstanceId))
                .body("data.Travels[0].metadata.processInstances[0].processId", is(processId))
                .body("data.Travels[0].metadata.processInstances[0].processName", is(processEvent.getData().getProcessName()))
                .body("data.Travels[0].metadata.processInstances[0].rootProcessId", is(nullValue()))
                .body("data.Travels[0].metadata.processInstances[0].rootProcessInstanceId", is(nullValue()))
                .body("data.Travels[0].metadata.processInstances[0].parentProcessInstanceId", is(nullValue()))
                .body("data.Travels[0].metadata.processInstances[0].lastUpdate",
                        is(formatZonedDateTime(processEvent.getTime().withZoneSameInstant(ZoneOffset.UTC))))
                .body("data.Travels[0].metadata.processInstances[0].endpoint", is(processEvent.getSource().toString()))
                .body("data.Travels[0].metadata.processInstances[0].serviceUrl", is("http://localhost:8080"));

        KogitoUserTaskCloudEvent userTaskEvent = getUserTaskCloudEvent(taskId, processId, processInstanceId, null, null, state);
        indexUserTaskCloudEvent(userTaskEvent);

        given().contentType(ContentType.JSON)
                .body(getTravelsByUserTaskId(taskId))
                .when().post("/graphql")
                .then().log().ifValidationFails().statusCode(200)
                .body("data.Travels[0].id", is(processInstanceId))
                .body("data.Travels[0].flight.flightNumber", is("MX555"))
                .body("data.Travels[0].hotel.name", is("Meriton"))
                .body("data.Travels[0].traveller.firstName", is("Maciej"))
                .body("data.Travels[0].metadata.lastUpdate",
                        is(formatZonedDateTime(userTaskEvent.getTime().withZoneSameInstant(ZoneOffset.UTC))))
                .body("data.Travels[0].metadata.userTasks.size()", is(1))
                .body("data.Travels[0].metadata.userTasks[0].id", is(taskId))
                .body("data.Travels[0].metadata.userTasks[0].processInstanceId", is(processInstanceId))
                .body("data.Travels[0].metadata.userTasks[0].description", is(userTaskEvent.getData().getDescription()))
                .body("data.Travels[0].metadata.userTasks[0].name", is(userTaskEvent.getData().getName()))
                .body("data.Travels[0].metadata.userTasks[0].priority", is(userTaskEvent.getData().getPriority()))
                .body("data.Travels[0].metadata.userTasks[0].actualOwner", is(userTaskEvent.getData().getActualOwner()))
                .body("data.Travels[0].metadata.userTasks[0].lastUpdate",
                        is(formatZonedDateTime(userTaskEvent.getTime().withZoneSameInstant(ZoneOffset.UTC))))
                .body("data.Travels[0].metadata.processInstances.size()", is(1))
                .body("data.Travels[0].metadata.processInstances[0].id", is(processInstanceId))
                .body("data.Travels[0].metadata.processInstances[0].processId", is(processId))
                .body("data.Travels[0].metadata.processInstances[0].processName", is(processEvent.getData().getProcessName()))
                .body("data.Travels[0].metadata.processInstances[0].rootProcessId", is(nullValue()))
                .body("data.Travels[0].metadata.processInstances[0].rootProcessInstanceId", is(nullValue()))
                .body("data.Travels[0].metadata.processInstances[0].parentProcessInstanceId", is(nullValue()))
                .body("data.Travels[0].metadata.processInstances[0].lastUpdate",
                        is(formatZonedDateTime(processEvent.getTime().withZoneSameInstant(ZoneOffset.UTC))))
                .body("data.Travels[0].metadata.processInstances[0].endpoint", is(processEvent.getSource().toString()))
                .body("data.Travels[0].metadata.processInstances[0].serviceUrl", is("http://localhost:8080"));
    }

    @Test
    void testIndexingDomainParallelEvents() throws Exception {
        String taskId = UUID.randomUUID().toString();
        String state = "InProgress";
        String processId = "travels";
        String processInstanceId = UUID.randomUUID().toString();

        protobufService.registerProtoBufferType(getProcessProtobufFileContent());

        given().contentType(ContentType.JSON).body("{ \"query\" : \"{ Travels{ id } }\" }")
                .when().post("/graphql")
                .then().log().ifValidationFails().statusCode(200).body("data.Travels", isA(Collection.class));

        KogitoProcessCloudEvent processEvent = getProcessCloudEvent(processId, processInstanceId, ACTIVE, null, null, null);
        KogitoUserTaskCloudEvent userTaskEvent = getUserTaskCloudEvent(taskId, processId, processInstanceId, null, null, state);

        CompletableFuture.allOf(
                CompletableFuture.runAsync(() -> indexProcessCloudEvent(processEvent)),
                CompletableFuture.runAsync(() -> indexUserTaskCloudEvent(userTaskEvent)))
                .get();

        given().contentType(ContentType.JSON)
                .body(getTravelsByProcessInstanceId(processInstanceId))
                .when().post("/graphql")
                .then().log().ifValidationFails().statusCode(200)
                .body("data.Travels[0].id", is(processInstanceId))
                .body("data.Travels[0].flight.flightNumber", is("MX555"))
                .body("data.Travels[0].hotel.name", is("Meriton"))
                .body("data.Travels[0].traveller.firstName", is("Maciej"))
                .body("data.Travels[0].metadata.lastUpdate", anyOf(Arrays
                        .asList(is(formatZonedDateTime(userTaskEvent.getTime().withZoneSameInstant(ZoneOffset.UTC))),
                                is((formatZonedDateTime(processEvent.getTime().withZoneSameInstant(ZoneOffset.UTC)))))))
                .body("data.Travels[0].metadata.userTasks.size()", is(1))
                .body("data.Travels[0].metadata.userTasks[0].id", is(taskId))
                .body("data.Travels[0].metadata.userTasks[0].processInstanceId", is(processInstanceId))
                .body("data.Travels[0].metadata.userTasks[0].description", is(userTaskEvent.getData().getDescription()))
                .body("data.Travels[0].metadata.userTasks[0].name", is(userTaskEvent.getData().getName()))
                .body("data.Travels[0].metadata.userTasks[0].priority", is(userTaskEvent.getData().getPriority()))
                .body("data.Travels[0].metadata.userTasks[0].actualOwner", is(userTaskEvent.getData().getActualOwner()))
                .body("data.Travels[0].metadata.userTasks[0].lastUpdate",
                        is(formatZonedDateTime(userTaskEvent.getTime().withZoneSameInstant(ZoneOffset.UTC))))
                .body("data.Travels[0].metadata.processInstances.size()", is(1))
                .body("data.Travels[0].metadata.processInstances[0].id", is(processInstanceId))
                .body("data.Travels[0].metadata.processInstances[0].processId", is(processId))
                .body("data.Travels[0].metadata.processInstances[0].processName", is(processEvent.getData().getProcessName()))
                .body("data.Travels[0].metadata.processInstances[0].rootProcessId", is(nullValue()))
                .body("data.Travels[0].metadata.processInstances[0].rootProcessInstanceId", is(nullValue()))
                .body("data.Travels[0].metadata.processInstances[0].parentProcessInstanceId", is(nullValue()))
                .body("data.Travels[0].metadata.processInstances[0].lastUpdate",
                        is(formatZonedDateTime(processEvent.getTime().withZoneSameInstant(ZoneOffset.UTC))))
                .body("data.Travels[0].metadata.processInstances[0].endpoint", is(processEvent.getSource().toString()))
                .body("data.Travels[0].metadata.processInstances[0].serviceUrl", is("http://localhost:8080"));
    }

    @Test
    void testProcessInstanceDomainIndex() throws Exception {
        String processId = "travels";
        String processInstanceId = UUID.randomUUID().toString();
        String subProcessId = processId + "_sub";
        String subProcessInstanceId = UUID.randomUUID().toString();

        protobufService.registerProtoBufferType(getProcessProtobufFileContent());

        given().contentType(ContentType.JSON).body("{ \"query\" : \"{ Travels{ id } }\" }")
                .when().post("/graphql")
                .then().log().ifValidationFails().statusCode(200).body("data.Travels", isA(Collection.class));

        KogitoProcessCloudEvent startEvent = getProcessCloudEvent(processId, processInstanceId, ACTIVE, null, null, null);
        indexProcessCloudEvent(startEvent);

        validateProcessInstance(getProcessInstanceById(processInstanceId), startEvent);

        given().contentType(ContentType.JSON)
                .body(getTravelsByProcessInstanceId(processInstanceId))
                .when().post("/graphql")
                .then().log().ifValidationFails().statusCode(200)
                .body("data.Travels[0].id", is(processInstanceId))
                .body("data.Travels[0].metadata.lastUpdate",
                        is(formatZonedDateTime(startEvent.getTime().withZoneSameInstant(ZoneOffset.UTC))))
                .body("data.Travels[0].metadata.processInstances.size()", is(1))
                .body("data.Travels[0].metadata.processInstances[0].id", is(processInstanceId))
                .body("data.Travels[0].metadata.processInstances[0].processId", is(processId))
                .body("data.Travels[0].metadata.processInstances[0].processName", is(startEvent.getData().getProcessName()))
                .body("data.Travels[0].metadata.processInstances[0].rootProcessId", is(nullValue()))
                .body("data.Travels[0].metadata.processInstances[0].rootProcessInstanceId", is(nullValue()))
                .body("data.Travels[0].metadata.processInstances[0].parentProcessInstanceId", is(nullValue()))
                .body("data.Travels[0].metadata.processInstances[0].state", is(ACTIVE.name()))
                .body("data.Travels[0].metadata.processInstances[0].start",
                        is(formatZonedDateTime(startEvent.getData().getStart().withZoneSameInstant(ZoneOffset.UTC))))
                .body("data.Travels[0].metadata.processInstances[0].lastUpdate",
                        is(formatZonedDateTime(startEvent.getData().getLastUpdate().withZoneSameInstant(ZoneOffset.UTC))))
                .body("data.Travels[0].metadata.processInstances[0].end", is(nullValue()))
                .body("data.Travels[0].metadata.processInstances[0].endpoint", is(startEvent.getSource().toString()))
                .body("data.Travels[0].metadata.processInstances[0].serviceUrl", is("http://localhost:8080"))
                .body("data.Travels[0].flight.flightNumber", is("MX555"))
                .body("data.Travels[0].hotel.name", is("Meriton"))
                .body("data.Travels[0].traveller.firstName", is("Maciej"));

        KogitoProcessCloudEvent endEvent = getProcessCloudEvent(processId, processInstanceId, COMPLETED, null, null, null);
        endEvent.getData().setEnd(ZonedDateTime.now());
        endEvent.getData().setVariables((ObjectNode) getObjectMapper().readTree(
                "{ \"traveller\":{\"firstName\":\"Maciej\"},\"hotel\":{\"name\":\"Ibis\"},\"flight\":{\"arrival\":\"2019-08-20T22:12:57.340Z\",\"departure\":\"2019-08-20T07:12:57.340Z\",\"flightNumber\":\"QF444\"} }"));
        indexProcessCloudEvent(endEvent);

        validateProcessInstance(getProcessInstanceByIdAndState(processInstanceId, COMPLETED), endEvent);

        given().contentType(ContentType.JSON)
                .body(getTravelsByProcessInstanceId(processInstanceId))
                .when().post("/graphql")
                .then().log().ifValidationFails().statusCode(200)
                .body("data.Travels[0].id", is(processInstanceId))
                .body("data.Travels[0].metadata.lastUpdate",
                        is(formatZonedDateTime(endEvent.getTime().withZoneSameInstant(ZoneOffset.UTC))))
                .body("data.Travels[0].metadata.processInstances.size()", is(1))
                .body("data.Travels[0].metadata.processInstances[0].id", is(processInstanceId))
                .body("data.Travels[0].metadata.processInstances[0].processId", is(processId))
                .body("data.Travels[0].metadata.processInstances[0].processName", is(endEvent.getData().getProcessName()))
                .body("data.Travels[0].metadata.processInstances[0].rootProcessId", is(nullValue()))
                .body("data.Travels[0].metadata.processInstances[0].rootProcessInstanceId", is(nullValue()))
                .body("data.Travels[0].metadata.processInstances[0].parentProcessInstanceId", is(nullValue()))
                .body("data.Travels[0].metadata.processInstances[0].state", is(COMPLETED.name()))
                .body("data.Travels[0].metadata.processInstances[0].start",
                        is(formatZonedDateTime(endEvent.getData().getStart().withZoneSameInstant(ZoneOffset.UTC))))
                .body("data.Travels[0].metadata.processInstances[0].end",
                        is(formatZonedDateTime(endEvent.getData().getEnd().withZoneSameInstant(ZoneOffset.UTC))))
                .body("data.Travels[0].metadata.processInstances[0].endpoint", is(endEvent.getSource().toString()))
                .body("data.Travels[0].metadata.processInstances[0].serviceUrl", is("http://localhost:8080"))
                .body("data.Travels[0].flight.flightNumber", is("QF444"))
                .body("data.Travels[0].hotel.name", is("Ibis"))
                .body("data.Travels[0].traveller.firstName", is("Maciej"));

        KogitoProcessCloudEvent event = getProcessCloudEvent(subProcessId, subProcessInstanceId, ACTIVE, processInstanceId,
                processId, processInstanceId);
        indexProcessCloudEvent(event);

        validateProcessInstance(getProcessInstanceByParentProcessInstanceId(processInstanceId), event);

        KogitoProcessCloudEvent errorEvent = getProcessCloudEvent(subProcessId, subProcessInstanceId, ERROR, processInstanceId,
                processId, processInstanceId);
        indexProcessCloudEvent(errorEvent);

        validateProcessInstance(
                getProcessInstanceByIdAndErrorNode(subProcessInstanceId, errorEvent.getData().getError().getNodeDefinitionId()),
                errorEvent);
    }

    @Test
    void testUserTaskInstanceDomainIndex() throws Exception {
        String taskId = UUID.randomUUID().toString();
        String state = "InProgress";
        String processId = "deals";
        String processInstanceId = UUID.randomUUID().toString();

        protobufService.registerProtoBufferType(getUserTaskProtobufFileContent());

        given().contentType(ContentType.JSON).body("{ \"query\" : \"{ Deals{ id } }\" }")
                .when().post("/graphql")
                .then().log().ifValidationFails().statusCode(200).body("data.Deals", isA(Collection.class));

        KogitoUserTaskCloudEvent event = getUserTaskCloudEvent(taskId, processId, processInstanceId, null, null, state);
        indexUserTaskCloudEvent(event);

        validateUserTaskInstance(getUserTaskInstanceById(taskId), event);

        given().contentType(ContentType.JSON)
                .body(getDealsByTaskId(taskId))
                .when().post("/graphql")
                .then().log().ifValidationFails().statusCode(200)
                .body("data.Deals[0].id", is(processInstanceId))
                .body("data.Deals[0].__typename", is("Deals"))
                .body("data.Deals[0].metadata.lastUpdate",
                        is(formatZonedDateTime(event.getTime().withZoneSameInstant(ZoneOffset.UTC))))
                .body("data.Deals[0].metadata.userTasks.size()", is(1))
                .body("data.Deals[0].metadata.userTasks[0].id", is(taskId))
                .body("data.Deals[0].metadata.userTasks[0].description", is("TaskDescription"))
                .body("data.Deals[0].metadata.userTasks[0].state", is("InProgress"))
                .body("data.Deals[0].metadata.userTasks[0].name", is("TaskName"))
                .body("data.Deals[0].metadata.userTasks[0].priority", is("High"))
                .body("data.Deals[0].metadata.userTasks[0].actualOwner", is("kogito"))
                .body("data.Deals[0].metadata.userTasks[0].started",
                        is(formatZonedDateTime(event.getData().getStarted().withZoneSameInstant(ZoneOffset.UTC))))
                .body("data.Deals[0].metadata.userTasks[0].completed",
                        is(formatZonedDateTime(event.getData().getCompleted().withZoneSameInstant(ZoneOffset.UTC))))
                .body("data.Deals[0].metadata.userTasks[0].lastUpdate",
                        is(formatZonedDateTime(event.getTime().withZoneSameInstant(ZoneOffset.UTC))));

        event = getUserTaskCloudEvent(taskId, processId, processInstanceId, null, null, state);
        event.getData().setCompleted(ZonedDateTime.now());
        event.getData().setPriority("Low");
        event.getData().setActualOwner("admin");
        event.getData().setState("Completed");

        indexUserTaskCloudEvent(event);

        validateUserTaskInstance(getUserTaskInstanceByIdAndActualOwner(taskId, "admin"), event);

        given().contentType(ContentType.JSON)
                .body(getDealsByTaskId(taskId))
                .when().post("/graphql")
                .then().log().ifValidationFails().statusCode(200)
                .body("data.Deals[0].id", is(processInstanceId))
                .body("data.Deals[0].__typename", is("Deals"))
                .body("data.Deals[0].metadata.lastUpdate",
                        is(formatZonedDateTime(event.getTime().withZoneSameInstant(ZoneOffset.UTC))))
                .body("data.Deals[0].metadata.userTasks.size()", is(1))
                .body("data.Deals[0].metadata.userTasks[0].id", is(taskId))
                .body("data.Deals[0].metadata.userTasks[0].description", is("TaskDescription"))
                .body("data.Deals[0].metadata.userTasks[0].state", is("Completed"))
                .body("data.Deals[0].metadata.userTasks[0].name", is("TaskName"))
                .body("data.Deals[0].metadata.userTasks[0].priority", is("Low"))
                .body("data.Deals[0].metadata.userTasks[0].actualOwner", is("admin"))
                .body("data.Deals[0].metadata.userTasks[0].started",
                        is(formatZonedDateTime(event.getData().getStarted().withZoneSameInstant(ZoneOffset.UTC))))
                .body("data.Deals[0].metadata.userTasks[0].completed",
                        is(formatZonedDateTime(event.getData().getCompleted().withZoneSameInstant(ZoneOffset.UTC))))
                .body("data.Deals[0].metadata.userTasks[0].lastUpdate",
                        is(formatZonedDateTime(event.getTime().withZoneSameInstant(ZoneOffset.UTC))));

        event = getUserTaskCloudEvent(taskId, processId, processInstanceId, null, null, state, null);
        indexUserTaskCloudEvent(event);

        validateUserTaskInstance(getUserTaskInstanceByIdNoActualOwner(taskId), event);

        given().contentType(ContentType.JSON)
                .body(getDealsByTaskIdNoActualOwner(taskId))
                .when().post("/graphql")
                .then().log().ifValidationFails().statusCode(200)
                .body("data.Deals[0].id", is(processInstanceId))
                .body("data.Deals[0].__typename", is("Deals"))
                .body("data.Deals[0].metadata.lastUpdate",
                        is(formatZonedDateTime(event.getTime().withZoneSameInstant(ZoneOffset.UTC))))
                .body("data.Deals[0].metadata.userTasks.size()", is(1))
                .body("data.Deals[0].metadata.userTasks[0].id", is(taskId))
                .body("data.Deals[0].metadata.userTasks[0].description", is("TaskDescription"))
                .body("data.Deals[0].metadata.userTasks[0].state", is("InProgress"))
                .body("data.Deals[0].metadata.userTasks[0].name", is("TaskName"))
                .body("data.Deals[0].metadata.userTasks[0].priority", is("High"))
                .body("data.Deals[0].metadata.userTasks[0].actualOwner", nullValue())
                .body("data.Deals[0].metadata.userTasks[0].started",
                        is(formatZonedDateTime(event.getData().getStarted().withZoneSameInstant(ZoneOffset.UTC))))
                .body("data.Deals[0].metadata.userTasks[0].completed",
                        is(formatZonedDateTime(event.getData().getCompleted().withZoneSameInstant(ZoneOffset.UTC))))
                .body("data.Deals[0].metadata.userTasks[0].lastUpdate",
                        is(formatZonedDateTime(event.getTime().withZoneSameInstant(ZoneOffset.UTC))));
    }

    private String getProtoBufferFileWithoutModelType() {
        return "package org.demo;\n" +
                "option kogito_id=\"travels\";\n" +
                "option kogito_model=\"traveller\";\n" +
                "/* @Indexed */\n" +
                "message travels {\n" +
                "   /* @Field(index = Index.NO, store = Store.YES) @SortableField */\n" +
                "   optional string traveller = 1;\n" +
                "   optional string hotel = 2;\n" +
                "   optional string flight = 3;\n" +
                "}\n" +
                "\n";
    }

    private String getProtoBufferFileWithoutId() {
        return "package org.demo;\n" +
                "option kogito_model=\"travels\";\n" +
                "/* @Indexed */\n" +
                "message travels {\n" +
                "   /* @Field(index = Index.NO, store = Store.YES) @SortableField */\n" +
                "   optional string traveller = 1;\n" +
                "   optional string hotel = 2;\n" +
                "   optional string flight = 3;\n" +
                "}\n" +
                "\n";
    }

    private String getProtoBufferFileWithoutModel() {
        return "package org.demo;\n" +
                "option kogito_id=\"travels\";\n" +
                "/* @Indexed */\n" +
                "message travels {\n" +
                "   /* @Field(index = Index.NO, store = Store.YES) @SortableField */\n" +
                "   optional string traveller = 1;\n" +
                "   optional string hotel = 2;\n" +
                "   optional string flight = 3;\n" +
                "}\n" +
                "\n";
    }

    private String getBrokenProtoBufferFile() {
        return "package org.demo;\n" +
                "/* @Indexed */\n" +
                "message travels {\n" +
                "   /* @Field(index = Index.NO, store = Store.YES) @SortableField */\n" +
                "   optional stringa traveller = 1;\n" +
                "   optional string hotel = 2;\n" +
                "   optional string flight = 3;\n" +
                "}\n" +
                "\n";
    }

    private String getProtoBufferFileV1() {
        return "package org.demo;\n" +
                "import \"kogito-index.proto\";\n" +
                "option kogito_model=\"Game\";\n" +
                "option kogito_id=\"game\";\n" +
                "/* @Indexed */\n" +
                "message Game {\n" +
                "   optional string player = 1;\n" +
                "   /* @Field(index = Index.YES, store = Store.YES) @SortableField */\n" +
                "   optional string id = 2;\n" +
                "   optional string name = 3;\n" +
                "   optional org.kie.kogito.index.model.KogitoMetadata metadata = 4;\n" +
                "}\n" +
                "\n";
    }

    private String getProtoBufferFileV2() {
        return "package org.demo;\n" +
                "import \"kogito-index.proto\";\n" +
                "option kogito_model=\"Game\";\n" +
                "option kogito_id=\"game\";\n" +
                "/* @Indexed */\n" +
                "message Game {\n" +
                "   /* @Field(index = Index.YES, store = Store.YES) @SortableField */\n" +
                "   optional string id = 1;\n" +
                "   optional string name = 2;\n" +
                "   optional string company = 3;\n" +
                "   optional org.kie.kogito.index.model.KogitoMetadata metadata = 4;\n" +
                "}\n" +
                "\n";
    }

    protected abstract String getProcessProtobufFileContent() throws Exception;

    protected abstract String getUserTaskProtobufFileContent() throws Exception;
}
