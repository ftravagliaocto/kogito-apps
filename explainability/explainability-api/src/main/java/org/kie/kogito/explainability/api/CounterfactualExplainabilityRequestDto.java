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
package org.kie.kogito.explainability.api;

import java.util.Map;
import java.util.Objects;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import org.kie.kogito.tracing.typedvalue.TypedValue;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CounterfactualExplainabilityRequestDto extends BaseExplainabilityRequestDto {

    public static final String EXPLAINABILITY_TYPE_NAME = "Counterfactual";

    public static final String COUNTERFACTUAL_ID_FIELD = "counterfactualId";

    public static final String ORIGINAL_INPUTS_FIELD = "originalInputs";

    public static final String GOALS_FIELD = "goals";

    public static final String SEARCH_DOMAINS_FIELD = "searchDomains";

    public static final String MAX_RUNNING_TIME_SECONDS_FIELD = "maxRunningTimeSeconds";

    @JsonProperty(COUNTERFACTUAL_ID_FIELD)
    @NotNull(message = "counterfactualId must be provided.")
    private String counterfactualId;

    @JsonProperty(ORIGINAL_INPUTS_FIELD)
    @NotNull(message = "originalInputs object must be provided.")
    private Map<String, TypedValue> originalInputs;

    @JsonProperty(GOALS_FIELD)
    @NotNull(message = "goals object must be provided.")
    private Map<String, TypedValue> goals;

    @JsonProperty(SEARCH_DOMAINS_FIELD)
    @NotNull(message = "searchDomains object must be provided.")
    private Map<String, CounterfactualSearchDomainDto> searchDomains;

    @JsonProperty(MAX_RUNNING_TIME_SECONDS_FIELD)
    @NotNull(message = "maxRunningTimeSeconds must be provided.")
    private Long maxRunningTimeSeconds;

    private CounterfactualExplainabilityRequestDto() {
        super();
    }

    public CounterfactualExplainabilityRequestDto(@NotNull String executionId,
            @NotNull String counterfactualId,
            @NotBlank String serviceUrl,
            @NotNull ModelIdentifierDto modelIdentifier,
            @NotNull Map<String, TypedValue> originalInputs,
            @NotNull Map<String, TypedValue> goals,
            @NotNull Map<String, CounterfactualSearchDomainDto> searchDomains,
            Long maxRunningTimeSeconds) {
        super(executionId, serviceUrl, modelIdentifier);
        this.counterfactualId = Objects.requireNonNull(counterfactualId);
        this.originalInputs = Objects.requireNonNull(originalInputs);
        this.goals = Objects.requireNonNull(goals);
        this.searchDomains = Objects.requireNonNull(searchDomains);
        this.maxRunningTimeSeconds = maxRunningTimeSeconds;
    }

    public String getCounterfactualId() {
        return counterfactualId;
    }

    public Map<String, TypedValue> getOriginalInputs() {
        return originalInputs;
    }

    public Map<String, TypedValue> getGoals() {
        return goals;
    }

    public Map<String, CounterfactualSearchDomainDto> getSearchDomains() {
        return searchDomains;
    }

    public Long getMaxRunningTimeSeconds() {
        return maxRunningTimeSeconds;
    }
}
