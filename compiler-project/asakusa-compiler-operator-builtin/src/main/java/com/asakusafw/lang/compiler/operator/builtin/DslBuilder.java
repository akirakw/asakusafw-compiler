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
package com.asakusafw.lang.compiler.operator.builtin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.processing.Messager;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

import com.asakusafw.lang.compiler.model.description.ClassDescription;
import com.asakusafw.lang.compiler.model.description.Descriptions;
import com.asakusafw.lang.compiler.model.description.EnumConstantDescription;
import com.asakusafw.lang.compiler.operator.CompileEnvironment;
import com.asakusafw.lang.compiler.operator.Constants;
import com.asakusafw.lang.compiler.operator.OperatorDriver;
import com.asakusafw.lang.compiler.operator.model.DataModelMirror;
import com.asakusafw.lang.compiler.operator.model.KeyMirror;
import com.asakusafw.lang.compiler.operator.model.OperatorDescription;
import com.asakusafw.lang.compiler.operator.model.OperatorDescription.Document;
import com.asakusafw.lang.compiler.operator.model.OperatorDescription.Node;
import com.asakusafw.lang.compiler.operator.model.OperatorDescription.Reference;
import com.asakusafw.lang.compiler.operator.model.OperatorDescription.SpecialReference;
import com.asakusafw.lang.compiler.operator.util.AnnotationHelper;

/**
 * Helper for built-in operators.
 */
final class DslBuilder {

    static final EnumConstantDescription CONSTANT_SHUFFLE = new EnumConstantDescription(
            new ClassDescription("com.asakusafw.vocabulary.flow.graph.FlowBoundary"), //$NON-NLS-1$
            "SHUFFLE"); //$NON-NLS-1$

    static final ClassDescription TYPE_LIST = Descriptions.classOf(List.class);

    static final ClassDescription TYPE_ENUM = Descriptions.classOf(Enum.class);

    static final ClassDescription TYPE_STRING = Descriptions.classOf(String.class);

    static final ClassDescription TYPE_VOLATILE =
            new ClassDescription("com.asakusafw.vocabulary.operator.Volatile"); //$NON-NLS-1$

    static final ClassDescription TYPE_STICKY =
            new ClassDescription("com.asakusafw.vocabulary.operator.Sticky"); //$NON-NLS-1$

    static final ClassDescription TYPE_OBSERVATION_COUNT =
            new ClassDescription("com.asakusafw.vocabulary.flow.graph.ObservationCount"); //$NON-NLS-1$

    private final List<Node> parameters = new ArrayList<>();

    private final List<Node> inputs = new ArrayList<>();

    private final List<Node> arguments = new ArrayList<>();

    private final List<Node> outputs = new ArrayList<>();

    private final List<KeyRef> keys = new ArrayList<>();

    private ExecutableElement support;

    private final List<EnumConstantDescription> attributes = new ArrayList<>();

    final CompileEnvironment environment;

    final ExecutableElement method;

    final AtomicBoolean errorSink = new AtomicBoolean();

    private final AnnotationRef annotationRef;

    private final ElementRef methodRef;

    private final List<ElementRef> parameterRefs;

    private final ElementRef resultRef;

    private final ElementRef unknownRef;

    public DslBuilder(OperatorDriver.Context context) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null"); //$NON-NLS-1$
        }
        this.environment = context.getEnvironment();
        this.method = context.getMethod();
        this.annotationRef = new AnnotationRef(context.getMethod(), context.getAnnotation());
        this.unknownRef = new MissingElementRef(context.getMethod());
        this.methodRef = new GeneralElementRef(context.getMethod(), Reference.method());
        this.parameterRefs = new ArrayList<>();
        int paramIndex = 0;
        for (VariableElement p : context.getMethod().getParameters()) {
            parameterRefs.add(new GeneralElementRef(p, Reference.parameter(paramIndex++)));
        }
        this.resultRef = new ResultElementRef(context.getMethod());
    }

    public void addInput(Document document, String name, TypeMirror type, Reference reference) {
        Node node = new Node(Node.Kind.INPUT, name, document, type, reference);
        inputs.add(node);
        parameters.add(node);
    }

    public void addInput(Document document, String name, TypeMirror type, KeyRef key, Reference reference) {
        Node node = new Node(Node.Kind.INPUT, name, document, type, reference);
        inputs.add(node);
        if (key != null) {
            node.withKey(key.getModel());
            keys.add(key);
        }
        parameters.add(node);

        // FIXME validate keys
    }

    public void addArgument(Document document, String name, TypeMirror type, Reference reference) {
        Node node = new Node(Node.Kind.DATA, name, document, type, reference);
        arguments.add(node);
        parameters.add(node);
    }

    public Node addOutput(Document document, String name, TypeMirror type, Reference reference) {
        Node node = new Node(Node.Kind.OUTPUT, name, document, type, reference);
        outputs.add(node);
        return node;
    }

    public void requireShuffle() {
        addAttribute(CONSTANT_SHUFFLE);
    }

    public void setSupport(ExecutableElement newValue) {
        this.support = newValue;
    }

    public void addAttribute(EnumConstantDescription attribute) {
        attributes.add(attribute);
    }

    public CompileEnvironment getEnvironment() {
        return environment;
    }

    public ExecutableElement getMethod() {
        return method;
    }

    public List<Node> getInputs() {
        return inputs;
    }

    public List<Node> getOutputs() {
        return outputs;
    }

    public List<Node> getParameters() {
        return parameters;
    }

    public boolean sawError() {
        return errorSink.get();
    }

    public OperatorDescription toDescription() {
        if (sawError()) {
            return null;
        }
        if (inputs.isEmpty()) {
            methodRef.error("Operator method must have at least one input parameter");
        }
        if (outputs.isEmpty()) {
            methodRef.error("Operator method must have at least one output parameter");
        }

        if (sawError()) {
            return null;
        }
        List<EnumConstantDescription> attrs = new ArrayList<>();
        attrs.addAll(attributes);
        attrs.add(computeObservationCount());
        return new OperatorDescription(Document.reference(Reference.method()), parameters, outputs, attrs)
            .withSupport(support);
    }

    private EnumConstantDescription computeObservationCount() {
        boolean isVolatile = false;
        boolean isSticky = false;
        DeclaredType volatileType = environment.findDeclaredType(TYPE_VOLATILE);
        DeclaredType stickyType = environment.findDeclaredType(TYPE_STICKY);
        Types types = environment.getProcessingEnvironment().getTypeUtils();
        for (AnnotationMirror mirror : method.getAnnotationMirrors()) {
            DeclaredType annotationType = mirror.getAnnotationType();
            if (isVolatile == false && types.isSameType(annotationType, volatileType)) {
                isVolatile = true;
            }
            if (isSticky == false && types.isSameType(annotationType, stickyType)) {
                isSticky = true;
            }
        }
        String name;
        if (isVolatile && isSticky) {
            name = "EXACTLY_ONCE"; //$NON-NLS-1$
        } else if (isVolatile) {
            name = "AT_MOST_ONCE"; //$NON-NLS-1$
        } else if (isSticky) {
            name = "AT_LEAST_ONCE"; //$NON-NLS-1$
        } else {
            name = "DONT_CARE"; //$NON-NLS-1$
        }
        return new EnumConstantDescription(TYPE_OBSERVATION_COUNT, name);
    }

    public ElementRef method() {
        return methodRef;
    }

    public ElementRef parameter(int index) {
        if (index < 0) {
            throw new IndexOutOfBoundsException();
        }
        if (index >= parameterRefs.size()) {
            return unknownRef;
        }
        return parameterRefs.get(index);
    }

    public ElementRef result() {
        return resultRef;
    }

    public List<ElementRef> parameters() {
        return parameters(0, parameterRefs.size());
    }

    public List<ElementRef> parameters(int from) {
        return parameters(from, parameterRefs.size());
    }

    public List<ElementRef> parameters(int from, int to) {
        if (0 <= from && from < to && to <= parameterRefs.size()) {
            return parameterRefs.subList(from, to);
        } else {
            return Collections.emptyList();
        }
    }

    public AnnotationRef annotation() {
        return annotationRef;
    }

    public void consumeArgument(ElementRef parameter) {
        if (parameter.type().isBasic()) {
            addArgument(parameter.document(), parameter.name(), parameter.type().mirror(), parameter.reference());
        } else {
            parameter.error("Value parameter must be primitive type or java.lang.String type");
        }
    }

    public boolean isGeneric() {
        return method.getTypeParameters().isEmpty() == false;
    }

    interface ElementRef {

        boolean exists();

        Element get();

        TypeRef type();

        Document document();

        Reference reference();

        String name();

        Set<Modifier> modifiers();

        KeyRef resolveKey(TypeRef modelType);

        KeyRef resolveKey(TypeRef modelType, AnnotationMirror annotation);

        void error(String string);
    }

    private class MissingElementRef implements ElementRef {

        private final Element owner;

        MissingElementRef(Element owner) {
            assert owner != null;
            this.owner = owner;
        }

        @Override
        public Element get() {
            return owner;
        }

        @Override
        public boolean exists() {
            return false;
        }

        @Override
        public TypeRef type() {
            return new TypeRef();
        }

        @Override
        public Document document() {
            return Document.text("MISSING");
        }

        @Override
        public Reference reference() {
            return Reference.special("UNKWON");
        }

        @Override
        public String name() {
            return "MISSING";
        }

        @Override
        public Set<Modifier> modifiers() {
            return Collections.emptySet();
        }

        @Override
        public KeyRef resolveKey(TypeRef modelType) {
            throw new IllegalStateException();
        }

        @Override
        public KeyRef resolveKey(TypeRef modelType, AnnotationMirror annotation) {
            throw new IllegalStateException();
        }

        @Override
        public void error(String message) {
            errorSink.set(true);
            Messager messager = environment.getProcessingEnvironment().getMessager();
            messager.printMessage(Diagnostic.Kind.ERROR, message, owner);
        }
    }

    private class GeneralElementRef implements ElementRef {

        final Element element;

        private final Reference reference;

        GeneralElementRef(Element element, Reference reference) {
            assert element != null;
            assert reference != null;
            this.element = element;
            this.reference = reference;
        }

        @Override
        public Element get() {
            return element;
        }

        @Override
        public boolean exists() {
            return true;
        }

        @Override
        public TypeRef type() {
            return new TypeRef(element.asType());
        }

        @Override
        public Document document() {
            return Document.reference(reference);
        }

        @Override
        public Reference reference() {
            return reference;
        }

        @Override
        public String name() {
            return element.getSimpleName().toString();
        }

        @Override
        public Set<Modifier> modifiers() {
            return element.getModifiers();
        }

        @Override
        public KeyRef resolveKey(TypeRef dataModelType) {
            TypeElement annotationType = environment.findTypeElement(Constants.TYPE_KEY);
            if (annotationType == null) {
                errorSink.set(true);
                return null;
            }
            AnnotationMirror annotation = AnnotationHelper.findAnnotation(environment, annotationType, element);
            if (annotation == null) {
                error("Failed to extract @Key annotation");
                return null;
            }
            return resolveKey(dataModelType, annotation);
        }

        @Override
        public KeyRef resolveKey(TypeRef modelType, AnnotationMirror annotation) {
            DataModelMirror dataModel = environment.findDataModel(modelType.mirror());
            if (dataModel == null) {
                errorSink.set(true);
                return null;
            }
            KeyMirror model = KeyMirror.parse(environment, annotation, element, dataModel);
            if (model == null) {
                errorSink.set(true);
                return null;
            }
            return new KeyRef(element, model);
        }

        @Override
        public void error(String message) {
            errorSink.set(true);
            Messager messager = environment.getProcessingEnvironment().getMessager();
            messager.printMessage(Diagnostic.Kind.ERROR, message, element);
        }
    }

    class ResultElementRef implements ElementRef {

        private final ExecutableElement element;

        ResultElementRef(ExecutableElement element) {
            assert element != null;
            this.element = element;
        }

        @Override
        public Element get() {
            return element;
        }

        @Override
        public boolean exists() {
            return true;
        }

        @Override
        public TypeRef type() {
            return new TypeRef(element.getReturnType());
        }

        @Override
        public Document document() {
            return Document.reference(reference());
        }

        @Override
        public Reference reference() {
            return Reference.returns();
        }

        @Override
        public String name() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Set<Modifier> modifiers() {
            return Collections.emptySet();
        }

        @Override
        public KeyRef resolveKey(TypeRef modelType) {
            throw new IllegalStateException();
        }

        @Override
        public KeyRef resolveKey(TypeRef modelType, AnnotationMirror annotation) {
            throw new IllegalStateException();
        }

        @Override
        public void error(String message) {
            errorSink.set(true);
            Messager messager = environment.getProcessingEnvironment().getMessager();
            messager.printMessage(Diagnostic.Kind.ERROR, message, element);
        }
    }

    class TypeRef {

        private final TypeMirror mirror;

        TypeRef() {
            this(environment.getProcessingEnvironment().getTypeUtils().getNoType(TypeKind.NONE));
        }

        TypeRef(TypeMirror mirror) {
            assert mirror != null;
            this.mirror = mirror;
        }

        private Types types() {
            return environment.getProcessingEnvironment().getTypeUtils();
        }

        public boolean exists() {
            return mirror.getKind() != TypeKind.NONE;
        }

        public boolean isVoid() {
            return mirror.getKind() == TypeKind.VOID;
        }

        public boolean isBasic() {
            return isPrimitive() || isString();
        }

        public boolean isBoolean() {
            return mirror.getKind() == TypeKind.BOOLEAN;
        }

        public boolean isPrimitive() {
            return mirror.getKind().isPrimitive();
        }

        public boolean isString() {
            return types().isSameType(mirror, environment.findDeclaredType(TYPE_STRING));
        }

        public boolean isEnum() {
            return types().isSubtype(mirror, environment.findDeclaredType(TYPE_ENUM));
        }

        public boolean isList() {
            return types().isSubtype(mirror, environment.findDeclaredType(TYPE_LIST));
        }

        public boolean isResult() {
            return types().isSubtype(mirror, environment.findDeclaredType(Constants.TYPE_RESULT));
        }

        public TypeRef arg(int index) {
            if (mirror.getKind() == TypeKind.DECLARED) {
                List<? extends TypeMirror> typeArguments = ((DeclaredType) mirror).getTypeArguments();
                if (0 <= index && index < typeArguments.size()) {
                    return new TypeRef(typeArguments.get(index));
                }
            }
            return new TypeRef();
        }

        public boolean isDataModel() {
            DataModelMirror dataModel = environment.findDataModel(mirror);
            return dataModel != null;
        }

        public boolean isEqualTo(TypeRef other) {
            return types().isSameType(mirror, other.mirror);
        }

        public TypeMirror mirror() {
            return mirror;
        }

        public DataModelMirror dataModel() {
            DataModelMirror dataModel = environment.findDataModel(mirror);
            if (isDataModel() == false) {
                throw new IllegalStateException();
            }
            return dataModel;
        }

        public List<ElementRef> enumConstants() {
            if (isEnum() == false) {
                throw new IllegalStateException();
            }
            TypeElement type = (TypeElement) ((DeclaredType) mirror).asElement();
            List<ElementRef> results = new ArrayList<>();
            for (VariableElement var : ElementFilter.fieldsIn(type.getEnclosedElements())) {
                if (var.getKind() == ElementKind.ENUM_CONSTANT) {
                    SpecialReference reference = Reference.special(var.getSimpleName().toString());
                    results.add(new GeneralElementRef(var, reference));
                }
            }
            return results;
        }

        public AnnotationRef annotation(ClassDescription annotationType) {
            if (mirror.getKind() != TypeKind.DECLARED) {
                return null;
            }
            TypeElement declaredType = (TypeElement) ((DeclaredType) mirror).asElement();
            if (declaredType == null) {
                return null;
            }
            TypeElement type = environment.findTypeElement(annotationType);
            if (type == null) {
                return null;
            }
            AnnotationMirror annotation = AnnotationHelper.findAnnotation(environment, type, declaredType);
            if (annotation == null) {
                return null;
            }
            return new AnnotationRef(declaredType, annotation);
        }

        @Override
        public String toString() {
            return mirror.toString();
        }
    }

    class AnnotationRef {

        private final AnnotationMirror annotation;

        private Map<String, AnnotationValue> values;

        private final Element holder;

        AnnotationRef(Element holder, AnnotationMirror annotation) {
            assert holder != null;
            assert annotation != null;
            this.holder = holder;
            this.annotation = annotation;
        }

        public AnnotationMirror get() {
            return annotation;
        }

        public AnnotationValue value(String name) {
            return getHolder(name);
        }

        public EnumConstantDescription constant(String name) {
            AnnotationValue valueHolder = getHolder(name);
            Object value = valueHolder.getValue();
            if (value instanceof VariableElement) {
                VariableElement var = (VariableElement) value;
                TypeElement type = (TypeElement) var.getEnclosingElement();
                return new EnumConstantDescription(
                        new ClassDescription(type.getQualifiedName().toString()),
                        var.getSimpleName().toString());
            }
            return null;
        }

        public String string(String name) {
            AnnotationValue valueHolder = getHolder(name);
            Object value = valueHolder.getValue();
            if (value instanceof String) {
                return (String) value;
            }
            return null;
        }

        public TypeRef type(String name) {
            AnnotationValue valueHolder = getHolder(name);
            Object value = valueHolder.getValue();
            if (value instanceof TypeMirror) {
                return new TypeRef((TypeMirror) value);
            }
            return null;
        }

        public AnnotationRef annotation(String name) {
            AnnotationValue valueHolder = getHolder(name);
            Object value = valueHolder.getValue();
            if (value instanceof AnnotationMirror) {
                return new AnnotationRef(holder, (AnnotationMirror) value);
            }
            return null;
        }

        public List<AnnotationRef> annotations(String name) {
            AnnotationValue valueHolder = getHolder(name);
            List<AnnotationValue> valueHolderList = AnnotationHelper.toValueList(environment, valueHolder);
            List<AnnotationMirror> annotations =
                    AnnotationHelper.extractList(environment, AnnotationMirror.class, valueHolderList);
            List<AnnotationRef> results = new ArrayList<>();
            for (AnnotationMirror component : annotations) {
                results.add(new AnnotationRef(method, component));
            }
            return results;
        }

        private AnnotationValue getHolder(String name) {
            AnnotationValue valueHolder = values().get(name);
            if (valueHolder == null) {
                throw new IllegalArgumentException(name);
            }
            return valueHolder;
        }

        private synchronized Map<String, AnnotationValue> values() {
            if (values == null) {
                values = AnnotationHelper.getValues(environment, annotation);
            }
            return values;
        }

        public void error(String message) {
            errorSink.set(true);
            Messager messager = environment.getProcessingEnvironment().getMessager();
            messager.printMessage(Diagnostic.Kind.ERROR, message, holder, annotation);
        }

        public void error(String elementName, String message) {
            AnnotationValue value = AnnotationHelper.getValue(environment, annotation, elementName);
            if (value == null) {
                error(message);
            } else {
                errorSink.set(true);
                Messager messager = environment.getProcessingEnvironment().getMessager();
                messager.printMessage(Diagnostic.Kind.ERROR, message, holder, annotation, value);
            }
        }
    }

    static class KeyRef {

        private final Element owner;

        private final KeyMirror model;

        KeyRef(Element owner, KeyMirror model) {
            this.owner = owner;
            this.model = model;
        }

        public Element getOwner() {
            return owner;
        }

        public KeyMirror getModel() {
            return model;
        }
    }
}
