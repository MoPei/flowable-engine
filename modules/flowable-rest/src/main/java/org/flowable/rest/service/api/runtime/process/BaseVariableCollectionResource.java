/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.flowable.rest.service.api.runtime.process;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.flowable.common.engine.api.FlowableIllegalArgumentException;
import org.flowable.common.rest.exception.FlowableConflictException;
import org.flowable.engine.runtime.Execution;
import org.flowable.rest.service.api.engine.variable.RestVariable;
import org.flowable.rest.service.api.engine.variable.RestVariable.RestVariableScope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * @author Tijs Rademakers
 */
public class BaseVariableCollectionResource extends BaseExecutionVariableResource {

    @Autowired
    protected ObjectMapper objectMapper;

    public BaseVariableCollectionResource(int variableType) {
        super(variableType);
    }

    protected List<RestVariable> processVariables(Execution execution, String scope) {
        Map<String, RestVariable> variableMap = new HashMap<>();

        // Check if it's a valid execution to get the variables for
        RestVariableScope variableScope = RestVariable.getScopeFromString(scope);

        if (variableScope == null) {
            // Use both local and global variables
            addLocalVariables(execution, variableMap);
            addGlobalVariables(execution, variableMap);

        } else if (variableScope == RestVariableScope.GLOBAL) {
            addGlobalVariables(execution, variableMap);

        } else if (variableScope == RestVariableScope.LOCAL) {
            addLocalVariables(execution, variableMap);
        }

        if (restApiInterceptor != null) {
            variableMap = restApiInterceptor.accessExecutionVariables(execution, variableMap);
        }

        // Get unique variables from map
        List<RestVariable> result = new ArrayList<>(variableMap.values());
        return result;
    }

    public void deleteAllLocalVariables(Execution execution) {
        Collection<String> currentVariables = runtimeService.getVariablesLocal(execution.getId()).keySet();
        if (restApiInterceptor != null) {
            restApiInterceptor.deleteExecutionVariables(execution, currentVariables, RestVariableScope.LOCAL);
        }
        runtimeService.removeVariablesLocal(execution.getId(), currentVariables);
    }

    protected Object createExecutionVariable(Execution execution, boolean override, boolean async, HttpServletRequest request, HttpServletResponse response) {
        Object result = null;
        if (request instanceof MultipartHttpServletRequest) {
            result = setBinaryVariable((MultipartHttpServletRequest) request, execution, true, async);
        } else {

            List<RestVariable> inputVariables = new ArrayList<>();
            List<RestVariable> resultVariables = new ArrayList<>();
            result = resultVariables;

            try {
                @SuppressWarnings("unchecked")
                List<Object> variableObjects = (List<Object>) objectMapper.readValue(request.getInputStream(), List.class);
                for (Object restObject : variableObjects) {
                    RestVariable restVariable = objectMapper.convertValue(restObject, RestVariable.class);
                    inputVariables.add(restVariable);
                }
            } catch (Exception e) {
                throw new FlowableIllegalArgumentException("Failed to serialize to a RestVariable instance", e);
            }

            if (inputVariables == null || inputVariables.size() == 0) {
                throw new FlowableIllegalArgumentException("Request did not contain a list of variables to create.");
            }

            RestVariableScope sharedScope = null;
            RestVariableScope varScope = null;
            Map<String, Object> variablesToSet = new HashMap<>();

            for (RestVariable var : inputVariables) {
                // Validate if scopes match
                varScope = var.getVariableScope();
                if (var.getName() == null) {
                    throw new FlowableIllegalArgumentException("Variable name is required");
                }

                if (varScope == null) {
                    varScope = RestVariableScope.LOCAL;
                }
                if (sharedScope == null) {
                    sharedScope = varScope;
                }
                if (varScope != sharedScope) {
                    throw new FlowableIllegalArgumentException("Only allowed to update multiple variables in the same scope.");
                }

                if (!override && hasVariableOnScope(execution, var.getName(), varScope)) {
                    throw new FlowableConflictException("Variable '" + var.getName() + "' is already present on execution '" + execution.getId() + "'.");
                }

                Object actualVariableValue = restResponseFactory.getVariableValue(var);
                variablesToSet.put(var.getName(), actualVariableValue);
            }

            if (!variablesToSet.isEmpty()) {
                if (restApiInterceptor != null) {
                    restApiInterceptor.createExecutionVariables(execution, variablesToSet, sharedScope);
                }

                Map<String, Object> setVariables = null;
                if (sharedScope == RestVariableScope.LOCAL) {
                    
                    if (async) {
                        runtimeService.setVariablesLocalAsync(execution.getId(), variablesToSet);
                        
                    } else {
                        runtimeService.setVariablesLocal(execution.getId(), variablesToSet);
                        setVariables = runtimeService.getVariablesLocal(execution.getId(), variablesToSet.keySet());
                    }
                    
                } else {
                    if (execution.getParentId() != null) {
                        // Explicitly set on parent, setting non-local variables on execution itself will override local-variables if exists
                        
                        if (async) {
                            runtimeService.setVariablesAsync(execution.getParentId(), variablesToSet);
                            
                        } else {
                            runtimeService.setVariables(execution.getParentId(), variablesToSet);
                            setVariables = runtimeService.getVariables(execution.getParentId(), variablesToSet.keySet());
                        }
                        
                    } else {
                        // Standalone task, no global variables possible
                        throw new FlowableIllegalArgumentException("Cannot set global variables on execution '" + execution.getId() + "', task is not part of process.");
                    }
                }

                if (!async) {
                    for (RestVariable inputVariable : inputVariables) {
                        String variableName = inputVariable.getName();
                        Object variableValue = setVariables.get(variableName);
                        resultVariables.add(restResponseFactory.createRestVariable(variableName, variableValue, varScope, execution.getId(), variableType, false));
                    }
                }

            }
        }
        response.setStatus(HttpStatus.CREATED.value());
        return result;
    }

    protected void addGlobalVariables(Execution execution, Map<String, RestVariable> variableMap) {
        Map<String, Object> rawVariables = runtimeService.getVariables(execution.getId());
        List<RestVariable> globalVariables = restResponseFactory.createRestVariables(rawVariables, execution.getId(), variableType, RestVariableScope.GLOBAL);

        // Overlay global variables over local ones. In case they are present
        // the values are not overridden,
        // since local variables get precedence over global ones at all times.
        for (RestVariable var : globalVariables) {
            if (!variableMap.containsKey(var.getName())) {
                variableMap.put(var.getName(), var);
            }
        }
    }

    protected void addLocalVariables(Execution execution, Map<String, RestVariable> variableMap) {
        Map<String, Object> rawLocalvariables = runtimeService.getVariablesLocal(execution.getId());
        List<RestVariable> localVariables = restResponseFactory.createRestVariables(rawLocalvariables, execution.getId(), variableType, RestVariableScope.LOCAL);

        for (RestVariable var : localVariables) {
            variableMap.put(var.getName(), var);
        }
    }
}
