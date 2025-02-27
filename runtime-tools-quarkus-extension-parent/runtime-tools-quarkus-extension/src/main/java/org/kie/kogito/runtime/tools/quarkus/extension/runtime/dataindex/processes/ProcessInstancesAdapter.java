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

package org.kie.kogito.runtime.tools.quarkus.extension.runtime.dataindex.processes;

import javax.json.JsonObject;
import javax.json.bind.adapter.JsonbAdapter;

public class ProcessInstancesAdapter implements JsonbAdapter<ProcessInstances, JsonObject> {

    @Override
    public JsonObject adaptToJson(final ProcessInstances processInstances) {
        return null; // not used
    }

    @Override
    public ProcessInstances adaptFromJson(final JsonObject jsonObject) {
        return new ProcessInstances(jsonObject.getJsonArray("ProcessInstances").getValuesAs(ProcessInstance.class));
    }
}
