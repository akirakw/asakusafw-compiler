/**
 * Copyright 2011-2015 Asakusa Framework Team.
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
package com.asakusafw.vocabulary.flow.builder;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.asakusafw.vocabulary.flow.FlowDescription;
import com.asakusafw.vocabulary.flow.Source;
import com.asakusafw.vocabulary.flow.graph.FlowElement;
import com.asakusafw.vocabulary.flow.graph.FlowElementAttribute;
import com.asakusafw.vocabulary.flow.graph.FlowElementDescription;
import com.asakusafw.vocabulary.flow.graph.FlowElementInput;
import com.asakusafw.vocabulary.flow.graph.FlowElementOutput;
import com.asakusafw.vocabulary.flow.graph.PortConnection;

/**
 * Builds operator graphs.
 */
public abstract class FlowElementBuilder {

    private final List<PortInfo> inputs = new ArrayList<>();

    private final List<PortInfo> outputs = new ArrayList<>();

    private final List<DataInfo> args = new ArrayList<>();

    private final List<FlowElementAttribute> attrs = new ArrayList<>();

    private final Map<String, FlowElementOutput> inputMapping = new HashMap<>();

    /**
     * Creates a new instance for operator method.
     * @param annotationType operator annotation type.
     * @param operatorClass operator class
     * @param methodName operator method name
     * @param methodParameterTypes the operator method parameter types
     * @return created builder
     * @throws IllegalArgumentException if some parameters were {@code null}
     */
    public static FlowElementBuilder createOperator(
            Class<? extends Annotation> annotationType,
            Class<?> operatorClass,
            String methodName,
            Class<?>... methodParameterTypes) {
        return new OperatorNodeBuilder(
                annotationType, operatorClass, operatorClass, methodName, methodParameterTypes);
    }

    /**
     * Creates a new instance for operator method.
     * @param annotationType operator annotation type.
     * @param operatorClass operator class
     * @param implementationClass operator implementation class
     * @param methodName operator method name
     * @param methodParameterTypes the operator method parameter types
     * @return created builder
     * @throws IllegalArgumentException if some parameters were {@code null}
     */
    public static FlowElementBuilder createOperator(
            Class<? extends Annotation> annotationType,
            Class<?> operatorClass,
            Class<?> implementationClass,
            String methodName,
            Class<?>... methodParameterTypes) {
        return new OperatorNodeBuilder(
                annotationType, operatorClass, implementationClass, methodName, methodParameterTypes);
    }

    /**
     * Creates a new instance for flow description class.
     * @param flowDescriptionClass flow description class
     * @param constructorParameterTypes constructor parameter types
     * @return created builder
     * @throws IllegalArgumentException if some parameters were {@code null}
     */
    public static FlowElementBuilder createFlow(
            Class<? extends FlowDescription> flowDescriptionClass,
            Class<?>... constructorParameterTypes) {
        return new FlowNodeBuilder(flowDescriptionClass, constructorParameterTypes);
    }

    /**
     * Defines a new input for operator.
     * @param name input name
     * @param upstream upstream dataset
     * @return defined port information
     * @throws IllegalArgumentException if some parameters were {@code null}
     */
    public PortInfo defineInput(String name, Source<?> upstream) {
        if (name == null) {
            throw new IllegalArgumentException("name must not be null"); //$NON-NLS-1$
        }
        if (upstream == null) {
            throw new IllegalArgumentException("upstream must not be null"); //$NON-NLS-1$
        }
        FlowElementOutput output = upstream.toOutputPort();
        PortInfo info = new PortInfo(PortInfo.Direction.INPUT, name, getType(output));
        inputs.add(info);
        inputMapping.put(name, output);
        return info;
    }

    private Type getType(FlowElementOutput output) {
        return output.getDescription().getDataType();
    }

    /**
     * Defines a new output for operator.
     * @param name output name
     * @param type output type
     * @return defined port information
     * @throws IllegalArgumentException if some parameters were {@code null}
     */
    public PortInfo defineOutput(String name, Type type) {
        if (name == null) {
            throw new IllegalArgumentException("name must not be null"); //$NON-NLS-1$
        }
        if (type == null) {
            throw new IllegalArgumentException("type must not be null"); //$NON-NLS-1$
        }
        return defineOutput0(name, type);
    }

    /**
     * Defines a new output for operator.
     * @param name output name
     * @param typeRef output type reference
     * @return defined port information
     * @throws IllegalArgumentException if some parameters were {@code null}
     */
    public PortInfo defineOutput(String name, Source<?> typeRef) {
        if (name == null) {
            throw new IllegalArgumentException("name must not be null"); //$NON-NLS-1$
        }
        if (typeRef == null) {
            throw new IllegalArgumentException("typeRef must not be null"); //$NON-NLS-1$
        }
        return defineOutput0(name, getType(typeRef.toOutputPort()));
    }

    private PortInfo defineOutput0(String name, Type type) {
        assert name != null;
        assert type != null;
        PortInfo info = new PortInfo(PortInfo.Direction.OUTPUT, name, type);
        outputs.add(info);
        return info;
    }

    /**
     * Defines a new data for operator.
     * @param name the argument name
     * @param data data representation
     * @return defined data information
     * @throws IllegalArgumentException if some parameters were {@code null}
     */
    public DataInfo defineData(String name, Data data) {
        if (name == null) {
            throw new IllegalArgumentException("name must not be null"); //$NON-NLS-1$
        }
        if (data == null) {
            throw new IllegalArgumentException("data must not be null"); //$NON-NLS-1$
        }
        DataInfo info = new DataInfo(name, data);
        args.add(info);
        return info;
    }

    /**
     * Defines a new argument for operator.
     * @param name the argument name
     * @param type the value type
     * @param value the constant value
     * @return defined data information
     * @throws IllegalArgumentException if some parameters were {@code null}
     */
    public DataInfo defineData(String name, Type type, Object value) {
        if (name == null) {
            throw new IllegalArgumentException("name must not be null"); //$NON-NLS-1$
        }
        return defineData(name, new Constant(type, value));
    }

    /**
     * Creates a new {@link KeyInfo} object.
     * @return the created object
     */
    public static KeyInfo key() {
        return new KeyInfo();
    }

    /**
     * Creates a new {@link ExternInfo} object.
     * @param name the external port name
     * @param description the external port description class
     * @return the created object
     */
    public static ExternInfo extern(String name, Class<?> description) {
        return new ExternInfo(name, description);
    }

    /**
     * Resolves current operator input/output/arguments.
     * @return the resolved information
     * @throws IllegalStateException if failed to resolve the operator
     */
    public FlowElementEditor resolve() {
        FlowElement resolved = new FlowElement(build(inputs, outputs, args, attrs));
        FlowElementEditor editor = new FlowElementEditor(resolved);
        for (Map.Entry<String, FlowElementOutput> entry : inputMapping.entrySet()) {
            FlowElementOutput upstream = entry.getValue();
            FlowElementInput downstream = editor.getInput(entry.getKey());
            PortConnection.connect(upstream, downstream);
        }
        return editor;
    }

    /**
     * Builds a flow from operator input/output/arguments.
     * @param inputPorts list of operator input
     * @param outputPorts list of operator output
     * @param arguments list of operator argument
     * @param attributes list of operator attribute
     * @return the resolved information
     * @throws IllegalStateException if failed to resolve the operator
     */
    protected abstract FlowElementDescription build(
            List<PortInfo> inputPorts,
            List<PortInfo> outputPorts,
            List<DataInfo> arguments,
            List<FlowElementAttribute> attributes);
}
