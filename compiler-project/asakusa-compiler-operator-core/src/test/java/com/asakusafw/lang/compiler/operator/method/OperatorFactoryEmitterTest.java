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
package com.asakusafw.lang.compiler.operator.method;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.ElementFilter;

import org.junit.Test;

import com.asakusafw.lang.compiler.model.description.ClassDescription;
import com.asakusafw.lang.compiler.model.description.Descriptions;
import com.asakusafw.lang.compiler.operator.Callback;
import com.asakusafw.lang.compiler.operator.CompileEnvironment;
import com.asakusafw.lang.compiler.operator.Constants;
import com.asakusafw.lang.compiler.operator.MockSource;
import com.asakusafw.lang.compiler.operator.OperatorCompilerTestRoot;
import com.asakusafw.lang.compiler.operator.StringDataModelMirrorRepository;
import com.asakusafw.lang.compiler.operator.model.KeyMirror;
import com.asakusafw.lang.compiler.operator.model.OperatorClass;
import com.asakusafw.lang.compiler.operator.model.OperatorDescription;
import com.asakusafw.lang.compiler.operator.model.OperatorDescription.Document;
import com.asakusafw.lang.compiler.operator.model.OperatorDescription.MethodReference;
import com.asakusafw.lang.compiler.operator.model.OperatorDescription.Node;
import com.asakusafw.lang.compiler.operator.model.OperatorDescription.Node.Kind;
import com.asakusafw.lang.compiler.operator.model.OperatorDescription.ParameterReference;
import com.asakusafw.lang.compiler.operator.model.OperatorDescription.ReferenceDocument;
import com.asakusafw.lang.compiler.operator.model.OperatorDescription.ReturnReference;
import com.asakusafw.lang.compiler.operator.model.OperatorDescription.TextDocument;
import com.asakusafw.lang.compiler.operator.model.OperatorElement;
import com.asakusafw.lang.compiler.operator.util.AnnotationHelper;
import com.asakusafw.vocabulary.flow.Source;
import com.asakusafw.vocabulary.flow.graph.FlowElement;
import com.asakusafw.vocabulary.flow.graph.FlowElementOutput;
import com.asakusafw.vocabulary.flow.graph.FlowElementPortDescription;
import com.asakusafw.vocabulary.flow.graph.OperatorDescription.Parameter;
import com.asakusafw.vocabulary.flow.graph.PortConnection;

/**
 * Test for {@link OperatorFactoryEmitter}.
 */
public class OperatorFactoryEmitterTest extends OperatorCompilerTestRoot {

    /**
     * Simple testing.
     */
    @Test
    public void simple() {
        Object factory = compile(new Action("com.example.Simple", "method") {
            @Override
            protected OperatorDescription analyze(ExecutableElement element) {
                Document document = new TextDocument("Hello, world!");
                List<Node> parameters = new ArrayList<>();
                List<Node> outputs = new ArrayList<>();
                return new OperatorDescription(document, parameters, outputs);
            }
        });
        Object node = invoke(factory, "method");
        assertThat(node.getClass().getName().replace('$', '.'), is("com.example.SimpleFactory.Method"));
    }

    /**
     * With input.
     */
    @Test
    public void input() {
        Object factory = compile(new Action("com.example.WithParameter", "method") {
            @Override
            protected OperatorDescription analyze(ExecutableElement element) {
                List<Node> parameters = new ArrayList<>();
                parameters.add(new Node(
                        Kind.INPUT,
                        "in",
                        new ReferenceDocument(new ParameterReference(0)),
                        env.findDeclaredType(Descriptions.classOf(String.class)),
                        new ParameterReference(0)));
                List<Node> outputs = new ArrayList<>();
                return new OperatorDescription(new ReferenceDocument(new MethodReference()), parameters, outputs);
            }
        });
        MockSource<String> source = MockSource.of(String.class);
        invoke(factory, "method", source);

        FlowElement info = getOppositeNode(source);
        assertThat(info.getInputPorts().size(), is(1));
        assertThat(info.getOutputPorts().size(), is(0));
        assertThat(getParameters(info).size(), is(0));
        FlowElementPortDescription port = info.getInputPorts().get(0).getDescription();
        assertThat(port.getName(), is("in"));
        assertThat(port.getDataType(), is((Object) String.class));
    }

    /**
     * With output.
     */
    @Test
    public void output() {
        Object factory = compile(new Action("com.example.WithParameter", "method") {
            @Override
            protected OperatorDescription analyze(ExecutableElement element) {
                List<Node> parameters = new ArrayList<>();
                List<Node> outputs = new ArrayList<>();
                outputs.add(new Node(
                        Kind.OUTPUT,
                        "out",
                        new ReferenceDocument(new ParameterReference(0)),
                        env.findDeclaredType(Descriptions.classOf(String.class)),
                        new ParameterReference(0)));
                return new OperatorDescription(new ReferenceDocument(new MethodReference()), parameters, outputs);
            }
        });
        Object node = invoke(factory, "method");
        assertThat(field(node.getClass(), "out"), is(notNullValue()));
        Object accessed = access(node, "out");
        assertThat(accessed, is(instanceOf(Source.class)));

        FlowElement info = getNode((Source<?>) accessed);
        assertThat(info.getInputPorts().size(), is(0));
        assertThat(info.getOutputPorts().size(), is(1));
        assertThat(getParameters(info).size(), is(0));
        FlowElementPortDescription port = info.getOutputPorts().get(0).getDescription();
        assertThat(port.getName(), is("out"));
        assertThat(port.getDataType(), is((Object) String.class));
    }

    /**
     * With argument.
     */
    @Test
    public void argument() {
        Object factory = compile(new Action("com.example.WithParameter", "method") {
            @Override
            protected OperatorDescription analyze(ExecutableElement element) {
                List<Node> parameters = new ArrayList<>();
                parameters.add(new Node(
                        Kind.DATA,
                        "arg",
                        new ReferenceDocument(new ParameterReference(0)),
                        env.findDeclaredType(Descriptions.classOf(String.class)),
                        new ParameterReference(0)));
                List<Node> outputs = new ArrayList<>();
                return new OperatorDescription(new ReferenceDocument(new MethodReference()), parameters, outputs);
            }
        });
        invoke(factory, "method", "Hello, world!");
    }

    /**
     * With type parameters.
     */
    @Test
    public void projective() {
        Object factory = compile(new Action("com.example.WithTypeParameter", "method") {
            @Override
            protected OperatorDescription analyze(ExecutableElement element) {
                List<Node> parameters = new ArrayList<>();
                parameters.add(new Node(
                        Kind.INPUT,
                        "in",
                        new ReferenceDocument(new ParameterReference(0)),
                        element.getTypeParameters().get(0).asType(),
                        new ParameterReference(0)));
                parameters.add(new Node(
                        Kind.DATA,
                        "arg",
                        new ReferenceDocument(new ParameterReference(1)),
                        env.findDeclaredType(Descriptions.classOf(String.class)),
                        new ParameterReference(1)));
                List<Node> outputs = new ArrayList<>();
                outputs.add(new Node(
                        Kind.OUTPUT,
                        "out",
                        new ReferenceDocument(new ReturnReference()),
                        element.getTypeParameters().get(0).asType(),
                        new ReturnReference()));
                return new OperatorDescription(new ReferenceDocument(new MethodReference()), parameters, outputs);
            }
        });
        MockSource<String> source = MockSource.of(String.class);
        invoke(factory, "method", source, "Hello, world!");

        FlowElement info = getOppositeNode(source);
        assertThat(info.getInputPorts().size(), is(1));
        assertThat(info.getOutputPorts().size(), is(1));
        assertThat(getParameters(info).size(), is(1));
    }

    /**
     * With key.
     */
    @Test
    public void key() {
        add(new StringDataModelMirrorRepository());
        Object factory = compile(new Action("com.example.WithKey", "method") {
            @Override
            protected OperatorDescription analyze(ExecutableElement element) {
                VariableElement param = element.getParameters().get(0);
                AnnotationMirror mirror = param.getAnnotationMirrors().get(0);
                List<Node> parameters = new ArrayList<>();
                parameters.add(new Node(
                        Kind.INPUT,
                        "in",
                        new ReferenceDocument(new ParameterReference(0)),
                        env.findDeclaredType(Descriptions.classOf(String.class)),
                        new ParameterReference(0))
                        .withKey(KeyMirror.parse(env, mirror, param, env.findDataModel(param.asType()))));
                List<Node> outputs = new ArrayList<>();
                return new OperatorDescription(new ReferenceDocument(new MethodReference()), parameters, outputs);
            }
        });
        MockSource<String> source = MockSource.of(String.class);
        invoke(factory, "method", source);

        FlowElement info = getOppositeNode(source);
        assertThat(info.getInputPorts().size(), is(1));
        assertThat(info.getOutputPorts().size(), is(0));
        assertThat(getParameters(info).size(), is(0));
        FlowElementPortDescription port = info.getInputPorts().get(0).getDescription();
        assertThat(port.getName(), is("in"));
        assertThat(port.getDataType(), is((Object) String.class));
    }

    private FlowElement getOppositeNode(Source<?> source) {
        FlowElementOutput output = source.toOutputPort();
        assertThat(output.getConnected().isEmpty(), is(false));
        for (PortConnection connection : output.getConnected()) {
            return connection.getDownstream().getOwner();
        }
        throw new AssertionError(source);
    }

    private FlowElement getNode(Source<?> source) {
        FlowElementOutput output = source.toOutputPort();
        return output.getOwner();
    }

    private List<Parameter> getParameters(FlowElement info) {
        return ((com.asakusafw.vocabulary.flow.graph.OperatorDescription) info.getDescription()).getParameters();
    }

    private Object compile(final Action action) {
        add(action.className);
        add("com.example.Mock");
        final ClassLoader classLoader = start(action);
        assertThat(action.performed, is(true));
        ClassDescription implClass = Constants.getFactoryClass(action.className);
        return create(classLoader, implClass);
    }

    private abstract class Action extends Callback {

        final String className;

        final Set<String> methodNames;

        boolean performed;

        Action(String className, String... methodNames) {
            this.className = className;
            this.methodNames = new HashSet<>();
            Collections.addAll(this.methodNames, methodNames);
        }

        @Override
        protected CompileEnvironment createCompileEnvironment(ProcessingEnvironment processingEnv) {
            return new CompileEnvironment(processingEnv, operatorDrivers, dataModelMirrors);
        }

        @Override
        protected void test() {
            TypeElement element = env.findTypeElement(new ClassDescription(className));
            if (round.getRootElements().contains(element)) {
                TypeElement annotationType = env.findTypeElement(new ClassDescription("com.example.Mock"));
                this.performed = true;
                List<OperatorElement> elems = new ArrayList<>();
                for (ExecutableElement e : ElementFilter.methodsIn(element.getEnclosedElements())) {
                    AnnotationMirror annotation = AnnotationHelper.findAnnotation(env, annotationType, e);
                    if (methodNames.contains(e.getSimpleName().toString())) {
                        OperatorDescription desc = analyze(e);
                        elems.add(new OperatorElement(annotation, e, desc));
                    }
                }
                OperatorClass analyzed = new OperatorClass(element, elems);
                new OperatorFactoryEmitter(env).emit(analyzed);
                new OperatorImplementationEmitter(env).emit(analyzed);
            }
        }

        protected abstract OperatorDescription analyze(ExecutableElement element);
    }
}
