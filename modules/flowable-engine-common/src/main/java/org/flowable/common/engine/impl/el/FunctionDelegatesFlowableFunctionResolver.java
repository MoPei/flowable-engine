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
package org.flowable.common.engine.impl.el;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.flowable.common.engine.api.delegate.FlowableFunctionDelegate;

/**
 * @author Filip Hrisafov
 */
public class FunctionDelegatesFlowableFunctionResolver implements FlowableFunctionResolver {

    protected final Map<String, FlowableFunctionDelegate> functionDelegateMap;
    protected final Map<String, Method> functionsCache = new HashMap<>();

    public FunctionDelegatesFlowableFunctionResolver(Collection<FlowableFunctionDelegate> functionDelegates) {
        functionDelegateMap = new LinkedHashMap<>();
        for (FlowableFunctionDelegate functionDelegate : functionDelegates) {
            for (String prefix : functionDelegate.prefixes()) {
                for (String localName : functionDelegate.localNames()) {
                    functionDelegateMap.put(prefix + ":" + localName, functionDelegate);
                }

            }

        }
    }

    @Override
    public Method resolveFunction(String prefix, String localName) throws NoSuchMethodException {
        String functionName = prefix + ":" + localName;
        if (functionsCache.containsKey(functionName)) {
            return functionsCache.get(functionName);
        }
        Method functionMethod = resolveFunction(functionDelegateMap.get(functionName), prefix, localName);
        functionsCache.put(functionName, functionMethod);
        return functionMethod;

    }

    protected Method resolveFunction(FlowableFunctionDelegate functionDelegate, String prefix, String localName) throws NoSuchMethodException {
        return functionDelegate != null ? functionDelegate.functionMethod(prefix, localName) : null;
    }
}
