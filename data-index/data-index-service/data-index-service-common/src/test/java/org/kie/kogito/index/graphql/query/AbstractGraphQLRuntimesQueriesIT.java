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
package org.kie.kogito.index.graphql.query;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kie.kogito.index.api.KogitoRuntimeClient;
import org.kie.kogito.index.event.KogitoJobCloudEvent;
import org.kie.kogito.index.event.KogitoProcessCloudEvent;
import org.kie.kogito.index.event.KogitoUserTaskCloudEvent;
import org.kie.kogito.index.graphql.GraphQLSchemaManager;
import org.kie.kogito.index.model.UserTaskInstance;
import org.kie.kogito.index.service.AbstractIndexingIT;
import org.kie.kogito.persistence.protobuf.ProtobufService;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import io.restassured.http.ContentType;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.kie.kogito.index.TestUtils.getJob;
import static org.kie.kogito.index.TestUtils.getJobCloudEvent;
import static org.kie.kogito.index.TestUtils.getProcessCloudEvent;
import static org.kie.kogito.index.TestUtils.getProcessInstance;
import static org.kie.kogito.index.TestUtils.getUserTaskCloudEvent;
import static org.kie.kogito.index.model.ProcessInstanceState.ACTIVE;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public abstract class AbstractGraphQLRuntimesQueriesIT extends AbstractIndexingIT {

    @Inject
    public GraphQLSchemaManager manager;

    @Inject
    public ProtobufService protobufService;

    private KogitoRuntimeClient dataIndexApiClient;

    @BeforeEach
    public void setup() throws Exception {
        protobufService.registerProtoBufferType(getTestProtobufFileContent());
        dataIndexApiClient = mock(KogitoRuntimeClient.class);
        manager.setDataIndexApiExecutor(dataIndexApiClient);
    }

    @Test
    void testProcessInstanceAbort() {
        String processId = "travels";
        String processInstanceId = UUID.randomUUID().toString();
        KogitoProcessCloudEvent startEvent = getProcessCloudEvent(processId, processInstanceId, ACTIVE, null, null, null);
        indexProcessCloudEvent(startEvent);

        checkOkResponse("{ \"query\" : \"mutation{ ProcessInstanceAbort ( id: \\\"" + processInstanceId + "\\\")}\"}");

        verify(dataIndexApiClient).abortProcessInstance(eq("http://localhost:8080"),
                eq(getProcessInstance(processId, processInstanceId, 1, null, null)));
    }

    @Test
    void testProcessInstanceRetry() {
        String processId = "travels";
        String processInstanceId = UUID.randomUUID().toString();
        KogitoProcessCloudEvent startEvent = getProcessCloudEvent(processId, processInstanceId, ACTIVE, null, null, null);
        indexProcessCloudEvent(startEvent);

        checkOkResponse("{ \"query\" : \"mutation{ ProcessInstanceRetry ( id: \\\"" + processInstanceId + "\\\")}\"}");

        verify(dataIndexApiClient).retryProcessInstance(eq("http://localhost:8080"),
                eq(getProcessInstance(processId, processInstanceId, 1, null, null)));
    }

    @Test
    void testProcessInstanceSkip() {
        String processId = "travels";
        String processInstanceId = UUID.randomUUID().toString();
        KogitoProcessCloudEvent startEvent = getProcessCloudEvent(processId, processInstanceId, ACTIVE, null, null, null);
        indexProcessCloudEvent(startEvent);

        checkOkResponse("{ \"query\" : \"mutation{ ProcessInstanceSkip ( id: \\\"" + processInstanceId + "\\\")}\"}");

        verify(dataIndexApiClient).skipProcessInstance(eq("http://localhost:8080"),
                eq(getProcessInstance(processId, processInstanceId, 1, null, null)));
    }

    @Test
    void testProcessInstanceUpdateVariables() {
        String processId = "travels";
        String variablesUpdated = "variablesUpdated";
        String processInstanceId = UUID.randomUUID().toString();

        KogitoProcessCloudEvent startEvent = getProcessCloudEvent(processId, processInstanceId, ACTIVE, null, null, null);
        indexProcessCloudEvent(startEvent);

        checkOkResponse("{ \"query\" : \"mutation{ ProcessInstanceUpdateVariables ( id: \\\"" + processInstanceId + "\\\", variables: \\\"" + variablesUpdated + "\\\")}\"}");

        verify(dataIndexApiClient).updateProcessInstanceVariables(eq("http://localhost:8080"),
                eq(getProcessInstance(processId, processInstanceId, 1, null, null)), eq(variablesUpdated));
    }

    @Test
    void testProcessInstanceNodeDefinitions() {
        String processId = "travels";
        String processInstanceId = UUID.randomUUID().toString();
        KogitoProcessCloudEvent startEvent = getProcessCloudEvent(processId, processInstanceId, ACTIVE, null, null, null);
        indexProcessCloudEvent(startEvent);

        checkOkResponse("{ \"query\" : \"query { ProcessInstances (where: { id: {equal: \\\"" + processInstanceId + "\\\"}}) { nodeDefinitions { id }} }\" }");
        verify(dataIndexApiClient).getProcessInstanceNodeDefinitions(eq("http://localhost:8080"),
                eq(getProcessInstance(processId, processInstanceId, 1, null, null)));
    }

    @Test
    void testProcessInstanceDiagram() {
        String processId = "travels";
        String processInstanceId = UUID.randomUUID().toString();
        KogitoProcessCloudEvent startEvent = getProcessCloudEvent(processId, processInstanceId, ACTIVE, null, null, null);
        indexProcessCloudEvent(startEvent);

        checkOkResponse("{ \"query\" : \"query { ProcessInstances (where: { id: {equal: \\\"" + processInstanceId + "\\\"}}) {diagram} }\" }");

        verify(dataIndexApiClient).getProcessInstanceDiagram(eq("http://localhost:8080"),
                eq(getProcessInstance(processId, processInstanceId, 1, null, null)));
    }

    @Test
    void testNodeInstanceTrigger() {
        String processId = "travels";
        String nodeId = "nodeIdToTrigger";
        String processInstanceId = UUID.randomUUID().toString();
        KogitoProcessCloudEvent startEvent = getProcessCloudEvent(processId, processInstanceId, ACTIVE, null, null, null);
        indexProcessCloudEvent(startEvent);

        checkOkResponse("{ \"query\" : \"mutation{ NodeInstanceTrigger ( id: \\\"" + processInstanceId + "\\\", nodeId: \\\"" + nodeId + "\\\")}\"}");

        verify(dataIndexApiClient).triggerNodeInstance(eq("http://localhost:8080"),
                eq(getProcessInstance(processId, processInstanceId, 1, null, null)), eq(nodeId));
    }

    @Test
    void testNodeInstanceRetrigger() {
        String processId = "travels";
        String nodeInstanceId = "nodeInstanceIdToRetrigger";
        String processInstanceId = UUID.randomUUID().toString();
        KogitoProcessCloudEvent startEvent = getProcessCloudEvent(processId, processInstanceId, ACTIVE, null, null, null);
        indexProcessCloudEvent(startEvent);

        checkOkResponse("{ \"query\" : \"mutation{ NodeInstanceRetrigger ( id: \\\"" + processInstanceId + "\\\", nodeInstanceId: \\\"" + nodeInstanceId + "\\\")}\"}");

        verify(dataIndexApiClient).retriggerNodeInstance(eq("http://localhost:8080"),
                eq(getProcessInstance(processId, processInstanceId, 1, null, null)), eq(nodeInstanceId));
    }

    @Test
    void testNodeInstanceCancel() {
        String processId = "travels";
        String nodeInstanceId = "nodeInstanceIdToCancel";
        String processInstanceId = UUID.randomUUID().toString();
        KogitoProcessCloudEvent startEvent = getProcessCloudEvent(processId, processInstanceId, ACTIVE, null, null, null);
        indexProcessCloudEvent(startEvent);

        checkOkResponse("{ \"query\" : \"mutation{ NodeInstanceCancel ( id: \\\"" + processInstanceId + "\\\", nodeInstanceId: \\\"" + nodeInstanceId + "\\\")}\"}");

        verify(dataIndexApiClient).cancelNodeInstance(eq("http://localhost:8080"),
                eq(getProcessInstance(processId, processInstanceId, 1, null, null)), eq(nodeInstanceId));
    }

    @Test
    void testJobCancel() {
        String jobId = UUID.randomUUID().toString();
        String processId = "travels";
        String processInstanceId = UUID.randomUUID().toString();

        KogitoJobCloudEvent event = getJobCloudEvent(jobId, processId, processInstanceId, null, null, "EXECUTED");

        indexJobCloudEvent(event);
        checkOkResponse("{ \"query\" : \"mutation{ JobCancel ( id: \\\"" + jobId + "\\\")}\"}");

        verify(dataIndexApiClient).cancelJob(eq("http://localhost:8080/jobs"),
                eq(getJob(jobId, processId, processInstanceId, null, null, "SCHEDULED")));
    }

    @Test
    void testJobReschedule() {
        String jobId = UUID.randomUUID().toString();
        String processId = "travels";
        String processInstanceId = UUID.randomUUID().toString();
        String data = "jobNewData";

        KogitoJobCloudEvent event = getJobCloudEvent(jobId, processId, processInstanceId, null, null, "EXECUTED");

        indexJobCloudEvent(event);
        checkOkResponse("{ \"query\" : \"mutation{ JobReschedule ( id: \\\"" + jobId + "\\\", data: \\\"" + data + "\\\")}\"}");

        verify(dataIndexApiClient).rescheduleJob(eq("http://localhost:8080/jobs"),
                eq(getJob(jobId, processId, processInstanceId, null, null, "SCHEDULED")),
                eq(data));
    }

    @Test
    void testGetTaskSchema() {
        String processId = "travels";
        String processInstanceId = UUID.randomUUID().toString();
        String taskId = UUID.randomUUID().toString();
        String user = "jdoe";
        List<String> groups = Arrays.asList("managers", "users", "IT");

        KogitoUserTaskCloudEvent event = getUserTaskCloudEvent(taskId, processId, processInstanceId, null,
                null, "InProgress", user);

        indexUserTaskCloudEvent(event);
        checkOkResponse("{ \"query\" : \"{UserTaskInstances (where: {id: {equal:\\\"" + taskId + "\\\" }}){ " +
                "schema ( user: \\\"" + user + "\\\", groups: [\\\"managers\\\", \\\"users\\\", \\\"IT\\\"] )" +
                "}}\"}");
        ArgumentCaptor<UserTaskInstance> userTaskInstanceCaptor = ArgumentCaptor.forClass(UserTaskInstance.class);

        verify(dataIndexApiClient).getUserTaskSchema(eq("http://localhost:8080"),
                userTaskInstanceCaptor.capture(),
                eq(user), eq(groups));
        assertUserTaskInstance(userTaskInstanceCaptor.getValue(), taskId, processId, processInstanceId, user);
    }

    @Test
    void testUpdateTask() {
        String processId = "travels";
        String processInstanceId = UUID.randomUUID().toString();
        String taskId = UUID.randomUUID().toString();
        String user = "jdoe";
        List<String> groups = Arrays.asList("managers", "users", "IT");
        String taskInfo = "{ description: \\\"NewDescription\\\"}";

        KogitoUserTaskCloudEvent event = getUserTaskCloudEvent(taskId, processId, processInstanceId, null,
                null, "InProgress", user);

        indexUserTaskCloudEvent(event);
        checkOkResponse("{ \"query\" : \"mutation { TaskUpdate ( " +
                "id: \\\"" + processInstanceId + "\\\", " +
                "taskId: \\\"" + taskId + "\\\"" +
                "user: \\\"" + user + "\\\", " +
                "groups: [\\\"managers\\\", \\\"users\\\", \\\"IT\\\"]," +
                "taskInfo:  " + taskInfo + "" +
                ")}\"}");
        ArgumentCaptor<UserTaskInstance> userTaskInstanceCaptor = ArgumentCaptor.forClass(UserTaskInstance.class);
        ArgumentCaptor<Map> taskInfoCaptor = ArgumentCaptor.forClass(Map.class);

        verify(dataIndexApiClient).updateUserTask(eq("http://localhost:8080"),
                userTaskInstanceCaptor.capture(),
                eq(user), eq(groups), taskInfoCaptor.capture());
        assertThat(taskInfoCaptor.getValue().get("description")).isEqualTo("NewDescription");
        assertUserTaskInstance(userTaskInstanceCaptor.getValue(), taskId, processId, processInstanceId, user);
    }

    @Test
    void testPartialUpdateTask() {
        String processId = "travels";
        String processInstanceId = UUID.randomUUID().toString();
        String taskId = UUID.randomUUID().toString();
        String user = "jdoe";
        List<String> groups = Arrays.asList("managers", "users", "IT");
        String taskInfo = "{ description: \\\"NewDescription\\\"}";

        KogitoUserTaskCloudEvent event = getUserTaskCloudEvent(taskId, processId, processInstanceId, null,
                null, "InProgress", user);

        indexUserTaskCloudEvent(event);
        checkOkResponse("{ \"query\" : \"mutation { TaskPartialUpdate ( " +
                "id: \\\"" + processInstanceId + "\\\", " +
                "taskId: \\\"" + taskId + "\\\"" +
                "user: \\\"" + user + "\\\", " +
                "groups: [\\\"managers\\\", \\\"users\\\", \\\"IT\\\"]," +
                "taskInfo:  " + taskInfo +
                ")}\"}");
        ArgumentCaptor<UserTaskInstance> userTaskInstanceCaptor = ArgumentCaptor.forClass(UserTaskInstance.class);
        ArgumentCaptor<Map> taskInfoCaptor = ArgumentCaptor.forClass(Map.class);

        verify(dataIndexApiClient).partialUpdateUserTask(eq("http://localhost:8080"),
                userTaskInstanceCaptor.capture(),
                eq(user), eq(groups), taskInfoCaptor.capture());
        assertThat(taskInfoCaptor.getValue().get("description")).isEqualTo("NewDescription");
        assertUserTaskInstance(userTaskInstanceCaptor.getValue(), taskId, processId, processInstanceId, user);
    }

    private void assertUserTaskInstance(UserTaskInstance userTaskInstance, String taskId, String processId,
            String processInstanceId, String actualOwner) {
        assertThat(userTaskInstance.getId()).isEqualTo(taskId);
        assertThat(userTaskInstance.getProcessId()).isEqualTo(processId);
        assertThat(userTaskInstance.getProcessInstanceId()).isEqualTo(processInstanceId);
        assertThat(userTaskInstance.getActualOwner()).isEqualTo(actualOwner);
    }

    private void checkOkResponse(String body) {
        given().contentType(ContentType.JSON)
                .body(body)
                .when().post("/graphql")
                .then().statusCode(200);
    }

    protected abstract String getTestProtobufFileContent() throws Exception;
}
