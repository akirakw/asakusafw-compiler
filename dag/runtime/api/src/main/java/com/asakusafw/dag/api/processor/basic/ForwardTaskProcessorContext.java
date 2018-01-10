/**
 * Copyright 2011-2018 Asakusa Framework Team.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.asakusafw.dag.api.processor.basic;

import java.util.Optional;

import com.asakusafw.dag.api.processor.TaskInfo;
import com.asakusafw.dag.api.processor.TaskProcessorContext;

/**
 * A {@link TaskProcessorContext} context which forwards method invocations to obtain properties and resources.
 * @since 0.4.0
 */
public interface ForwardTaskProcessorContext extends TaskProcessorContext, ForwardEdgeIoProcessorContext {

    @Override
    TaskProcessorContext getForward();

    @Override
    default String getVertexId() {
        return getForward().getVertexId();
    }

    @Override
    default String getTaskId() {
        return getForward().getTaskId();
    }

    @Override
    default Optional<TaskInfo> getTaskInfo() {
        return getForward().getTaskInfo();
    }
}
