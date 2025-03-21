/*
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

package org.flowable.engine.impl.persistence.entity;

import org.flowable.common.engine.impl.db.HasRevision;
import org.flowable.engine.history.HistoricActivityInstance;

/**
 * @author Christian Stettler
 * @author Joram Barrez
 */
public interface HistoricActivityInstanceEntity extends HistoricActivityInstance, HistoricScopeInstanceEntity, HasRevision {

    void setTransactionOrder(Integer transactionOrder);
    
    void setActivityId(String activityId);

    void setActivityName(String activityName);

    void setActivityType(String activityType);

    void setExecutionId(String executionId);

    void setAssignee(String assignee);

    void setCompletedBy(String completedBy);

    void setTaskId(String taskId);

    void setCalledProcessInstanceId(String calledProcessInstanceId);

    void setTenantId(String tenantId);

}
