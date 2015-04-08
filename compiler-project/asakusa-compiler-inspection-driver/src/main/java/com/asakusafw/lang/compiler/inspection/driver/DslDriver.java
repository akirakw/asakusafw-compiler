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
package com.asakusafw.lang.compiler.inspection.driver;

import static com.asakusafw.lang.compiler.inspection.driver.Util.*;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import com.asakusafw.lang.compiler.inspection.InspectionNode;
import com.asakusafw.lang.compiler.model.graph.Batch;
import com.asakusafw.lang.compiler.model.graph.BatchElement;
import com.asakusafw.lang.compiler.model.graph.CoreOperator;
import com.asakusafw.lang.compiler.model.graph.ExternalInput;
import com.asakusafw.lang.compiler.model.graph.ExternalOutput;
import com.asakusafw.lang.compiler.model.graph.FlowOperator;
import com.asakusafw.lang.compiler.model.graph.Jobflow;
import com.asakusafw.lang.compiler.model.graph.MarkerOperator;
import com.asakusafw.lang.compiler.model.graph.Operator;
import com.asakusafw.lang.compiler.model.graph.OperatorArgument;
import com.asakusafw.lang.compiler.model.graph.OperatorConstraint;
import com.asakusafw.lang.compiler.model.graph.OperatorGraph;
import com.asakusafw.lang.compiler.model.graph.OperatorInput;
import com.asakusafw.lang.compiler.model.graph.OperatorOutput;
import com.asakusafw.lang.compiler.model.graph.Operators;
import com.asakusafw.lang.compiler.model.graph.UserOperator;
import com.asakusafw.lang.compiler.model.info.ExternalInputInfo;
import com.asakusafw.lang.compiler.model.info.ExternalOutputInfo;

/**
 * Inspects Asakusa DSL elements.
 */
public class DslDriver {

    static final String PROPERTY_NAME = "name"; //$NON-NLS-1$

    static final String PROPERTY_TYPE = "type"; //$NON-NLS-1$

    static final String PROPERTY_MODULE = "module"; //$NON-NLS-1$

    /**
     * Inspects the target batch.
     * @param object the target batch
     * @return the inspection object
     */
    public InspectionNode inspect(Batch object) {
        InspectionNode node = new InspectionNode(id(object), "Batch"); //$NON-NLS-1$
        node.getProperties().putAll(extract(object));
        for (InspectionNode element : inspectJobflows(object.getElements()).values()) {
            node.withElement(element);
        }
        return node;
    }

    Map<BatchElement, InspectionNode> inspectJobflows(Collection<BatchElement> elements) {
        Map<BatchElement, InspectionNode> results = new LinkedHashMap<>();
        for (BatchElement element : elements) {
            InspectionNode node = inspect(element.getJobflow());
            addDependencyPorts(node);
            results.put(element, node);
        }
        for (Map.Entry<BatchElement, InspectionNode> entry : results.entrySet()) {
            BatchElement downstreamElement = entry.getKey();
            InspectionNode downstreamNode = entry.getValue();
            for (BatchElement upstreamElement : downstreamElement.getBlockerElements()) {
                InspectionNode upstreamNode = results.get(upstreamElement);
                assert upstreamNode != null;
                addDependency(upstreamNode, downstreamNode);
            }
        }
        return results;
    }

    /**
     * Inspects the target jobflow.
     * @param object the target jobflow
     * @return the inspection object
     */
    public InspectionNode inspect(Jobflow object) {
        InspectionNode node = new InspectionNode(id(object), "Jobflow"); //$NON-NLS-1$
        node.getProperties().putAll(extract(object));
        for (InspectionNode element : inspect(object.getOperatorGraph().getOperators()).values()) {
            node.withElement(element);
        }
        return node;
    }

    /**
     * Inspects the target operator graph.
     * @param id the node ID for the target object
     * @param object the target operator graph
     * @return the inspection object
     */
    public InspectionNode inspect(String id, OperatorGraph object) {
        InspectionNode node = new InspectionNode(id, "Graph"); //$NON-NLS-1$
        for (InspectionNode element : inspect(object.getOperators()).values()) {
            node.withElement(element);
        }
        return node;
    }

    Map<Operator, InspectionNode> inspect(Collection<? extends Operator> operators) {
        OperatorCount counter = new OperatorCount();
        Map<Operator, InspectionNode> results = new LinkedHashMap<>();
        for (Operator operator : Operators.getTransitiveConnected(operators)) {
            String id = counter.id(operator);
            InspectionNode node = inspect(id, operator);
            results.put(operator, node);
        }
        for (Map.Entry<Operator, InspectionNode> entry : results.entrySet()) {
            Operator operator = entry.getKey();
            InspectionNode cNode = entry.getValue();
            for (OperatorInput port : operator.getInputs()) {
                InspectionNode.Port cPort = cNode.getInputs().get(id(port));
                assert cPort != null;
                for (OperatorOutput opposite : port.getOpposites()) {
                    InspectionNode oNode = results.get(opposite.getOwner());
                    assert oNode != null;
                    InspectionNode.PortReference oRef = new InspectionNode.PortReference(oNode.getId(), id(opposite));
                    cPort.withOpposite(oRef);
                }
            }
            for (OperatorOutput port : operator.getOutputs()) {
                InspectionNode.Port cPort = cNode.getOutputs().get(id(port));
                assert cPort != null;
                for (OperatorInput opposite : port.getOpposites()) {
                    InspectionNode oNode = results.get(opposite.getOwner());
                    assert oNode != null;
                    InspectionNode.PortReference oRef = new InspectionNode.PortReference(oNode.getId(), id(opposite));
                    cPort.withOpposite(oRef);
                }
            }
        }
        return results;
    }

    InspectionNode inspect(String id, Operator object) {
        InspectionNode node = inspectFlat(id, object);
        for (OperatorInput port : object.getInputs()) {
            InspectionNode.Port p = new InspectionNode.Port(id(port));
            node.withInput(p);
            p.withProperty(PROPERTY_TYPE, port.getDataType().toString());
            if (port.getGroup() != null) {
                p.withProperty("group", port.getGroup().toString()); //$NON-NLS-1$
            }
        }
        for (OperatorOutput port : object.getOutputs()) {
            InspectionNode.Port p = new InspectionNode.Port(id(port));
            node.withOutput(p);
            p.withProperty(PROPERTY_TYPE, port.getDataType().toString());
        }
        for (OperatorArgument arg : object.getArguments()) {
            node.withProperty(String.format("arguments.%s", arg.getName()), arg.getValue().toString()); //$NON-NLS-1$
        }
        for (OperatorConstraint constraint : object.getConstraints()) {
            node.withProperty(String.format("constraints.%s", constraint.name()), "true"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        return node;
    }

    private InspectionNode inspectFlat(String id, Operator object) {
        switch (object.getOperatorKind()) {
        case CORE:
            return inspectFlat(id, (CoreOperator) object);
        case USER:
            return inspectFlat(id, (UserOperator) object);
        case FLOW:
            return inspectFlat(id, (FlowOperator) object);
        case INPUT:
            return inspectFlat(id, (ExternalInput) object);
        case OUTPUT:
            return inspectFlat(id, (ExternalOutput) object);
        case MARKER:
            return inspectFlat(id, (MarkerOperator) object);
        default:
            throw new AssertionError(object);
        }
    }

    private InspectionNode inspectFlat(String id, CoreOperator object) {
        String title = object.getCoreOperatorKind().toString();
        InspectionNode result = new InspectionNode(id, title);
        return result;
    }

    private InspectionNode inspectFlat(String id, UserOperator object) {
        String title = object.getAnnotation().getDeclaringClass().getSimpleName();
        InspectionNode result = new InspectionNode(id, title);
        result.withProperty("class", object.getMethod().getDeclaringClass().getClassName()); //$NON-NLS-1$
        result.withProperty("method", object.getMethod().getName()); //$NON-NLS-1$
        return result;

    }

    private InspectionNode inspectFlat(String id, FlowOperator object) {
        String title = "Flow"; //$NON-NLS-1$
        InspectionNode result = new InspectionNode(id, title);
        result.withProperty(PROPERTY_DESCRIPTION, object.getDescriptionClass().getClassName());
        for (InspectionNode element : inspect(object.getOperatorGraph().getOperators()).values()) {
            result.withElement(element);
        }
        return result;
    }

    private InspectionNode inspectFlat(String id, ExternalInput object) {
        String title = "Input"; //$NON-NLS-1$
        InspectionNode result = new InspectionNode(id, title);
        result.withProperty(PROPERTY_NAME, object.getName());
        result.withProperty(PROPERTY_TYPE, object.getDataType().toString());
        if (object.isExternal()) {
            ExternalInputInfo info = object.getInfo();
            result.withProperty(PROPERTY_DESCRIPTION, info.getDescriptionClass().getClassName());
            result.withProperty(PROPERTY_MODULE, info.getModuleName());
            result.withProperty("size", info.getDataSize().toString());
        }
        return result;
    }

    private InspectionNode inspectFlat(String id, ExternalOutput object) {
        String title = "Output"; //$NON-NLS-1$
        InspectionNode result = new InspectionNode(id, title);
        result.withProperty(PROPERTY_NAME, object.getName());
        result.withProperty(PROPERTY_TYPE, object.getDataType().toString());
        if (object.isExternal()) {
            ExternalOutputInfo info = object.getInfo();
            result.withProperty(PROPERTY_DESCRIPTION, info.getDescriptionClass().getClassName());
            result.withProperty(PROPERTY_MODULE, info.getModuleName());
        }
        return result;
    }

    private InspectionNode inspectFlat(String id, MarkerOperator object) {
        String title = "Marker"; //$NON-NLS-1$
        InspectionNode result = new InspectionNode(id, title);
        result.withProperty(PROPERTY_TYPE, object.getDataType().toString());
        for (Class<?> type : object.getAttributeTypes()) {
            Object attribute = object.getAttribute(type);
            result.withProperty(getAttributeKey(type), getAttributeValue(attribute));
        }
        return result;
    }

    private static class OperatorCount {

        private final Counter counter = new Counter();

        OperatorCount() {
            return;
        }

        public String id(Operator operator) {
            switch (operator.getOperatorKind()) {
            case CORE:
                return counter.fetchId("core"); //$NON-NLS-1$
            case USER:
                return counter.fetchId("user"); //$NON-NLS-1$
            case FLOW:
                return counter.fetchId("flow"); //$NON-NLS-1$
            case MARKER:
                return counter.fetchId("marker"); //$NON-NLS-1$
            case INPUT:
                return String.format("input-%s", ((ExternalInput) operator).getName()); //$NON-NLS-1$
            case OUTPUT:
                return String.format("output-%s", ((ExternalOutput) operator).getName()); //$NON-NLS-1$
            default:
                throw new AssertionError(operator);
            }
        }
    }
}