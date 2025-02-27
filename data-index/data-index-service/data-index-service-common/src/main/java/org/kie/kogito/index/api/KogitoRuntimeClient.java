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

package org.kie.kogito.index.api;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.kie.kogito.index.model.Job;
import org.kie.kogito.index.model.Node;
import org.kie.kogito.index.model.ProcessInstance;
import org.kie.kogito.index.model.UserTaskInstance;

public interface KogitoRuntimeClient {

    CompletableFuture<String> abortProcessInstance(String serviceURL, ProcessInstance processInstance);

    CompletableFuture<String> retryProcessInstance(String serviceURL, ProcessInstance processInstance);

    CompletableFuture<String> skipProcessInstance(String serviceURL, ProcessInstance processInstance);

    CompletableFuture<String> updateProcessInstanceVariables(String serviceURL, ProcessInstance processInstance, String variables);

    CompletableFuture<String> getProcessInstanceDiagram(String serviceURL, ProcessInstance processInstance);

    CompletableFuture<List<Node>> getProcessInstanceNodeDefinitions(String serviceURL, ProcessInstance processInstance);

    CompletableFuture<String> triggerNodeInstance(String serviceURL, ProcessInstance processInstance, String nodeDefinitionId);

    CompletableFuture<String> retriggerNodeInstance(String serviceURL, ProcessInstance processInstance, String nodeInstanceId);

    CompletableFuture<String> cancelNodeInstance(String serviceURL, ProcessInstance processInstance, String nodeInstanceId);

    CompletableFuture<String> cancelJob(String serviceURL, Job job);

    CompletableFuture<String> rescheduleJob(String serviceURL, Job job, String newJobData);

    CompletableFuture<String> getUserTaskSchema(String serviceURL, UserTaskInstance userTaskInstance, String user, List<String> groups);

    CompletableFuture<String> updateUserTask(String serviceURL, UserTaskInstance userTaskInstance, String user, List<String> groups, Map taskInfo);

    CompletableFuture<String> partialUpdateUserTask(String serviceURL, UserTaskInstance userTaskInstance, String user, List<String> groups, Map taskInfo);
}
