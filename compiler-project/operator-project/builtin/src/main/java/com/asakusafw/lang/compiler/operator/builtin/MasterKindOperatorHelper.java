/**
 * Copyright 2011-2016 Asakusa Framework Team.
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

import java.text.MessageFormat;
import java.util.List;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Types;

import com.asakusafw.lang.compiler.model.description.ClassDescription;
import com.asakusafw.lang.compiler.model.description.Descriptions;
import com.asakusafw.lang.compiler.operator.CompileEnvironment;
import com.asakusafw.lang.compiler.operator.Constants;
import com.asakusafw.lang.compiler.operator.builtin.DslBuilder.ElementRef;
import com.asakusafw.lang.compiler.operator.builtin.DslBuilder.KeyRef;
import com.asakusafw.lang.compiler.operator.builtin.DslBuilder.TypeRef;
import com.asakusafw.lang.compiler.operator.model.DataModelMirror;
import com.asakusafw.lang.compiler.operator.model.DataModelMirror.Kind;
import com.asakusafw.lang.compiler.operator.util.AnnotationHelper;

final class MasterKindOperatorHelper {

    private static final ClassDescription TYPE_LIST = Descriptions.classOf(List.class);

    private static final String NAME_SELECTION = "selection"; //$NON-NLS-1$

    private static final String NO_SELECTION = "-"; //$NON-NLS-1$

    public static void consumeMaster(DslBuilder dsl) {
        ElementRef p = dsl.parameter(0);
        TypeRef type = p.type();
        if (type.isDataModel()) {
            KeyRef key = p.resolveKey(type);
            dsl.addInput(p.document(), p.name(), p.type().mirror(), key, p.reference());
        } else {
            p.error("The first parameter of this operator must have data model type");
        }
    }

    public static void consumeTx(DslBuilder dsl) {
        ElementRef p = dsl.parameter(1);
        TypeRef type = p.type();
        if (type.isDataModel()) {
            KeyRef key = p.resolveKey(type);
            dsl.addInput(p.document(), p.name(), p.type().mirror(), key, p.reference());
        } else {
            p.error("The second parameter of this operator must have data model type");
        }
    }

    public static ExecutableElement extractMasterSelection(DslBuilder dsl) {
        String selection = dsl.annotation().string(NAME_SELECTION);
        if (selection == null || selection.equals(NO_SELECTION)) {
            return null;
        }
        ExecutableElement selector = findSelector(dsl, selection);
        if (selector == null) {
            return null;
        }
        validateSelectorDeclaration(dsl, selector);
        if (dsl.sawError()) {
            return null;
        }
        try {
            checkParameters(dsl.getEnvironment(), dsl.method, selector);
        } catch (ResolveException e) {
            dsl.annotation().error(NAME_SELECTION, e.getMessage());
        }
        return selector;
    }

    private static ExecutableElement findSelector(DslBuilder dsl, String selection) {
        assert selection != null;
        ClassDescription className = Constants.getBuiltinOperatorClass("MasterSelection"); //$NON-NLS-1$
        TypeElement annotationDecl = dsl.getEnvironment().findTypeElement(className);
        if (annotationDecl == null) {
            dsl.annotation().error(NAME_SELECTION, "Failed to resolve selector annotation type");
            return null;
        }

        ExecutableElement result = null;
        TypeElement declaring = (TypeElement) dsl.getMethod().getEnclosingElement();
        for (ExecutableElement element : ElementFilter.methodsIn(declaring.getEnclosedElements())) {
            if (element.getSimpleName().contentEquals(selection)) {
                AnnotationMirror annotation = AnnotationHelper.findAnnotation(
                        dsl.getEnvironment(),
                        annotationDecl,
                        element);
                if (annotation == null) {
                    continue;
                }
                if (result == null) {
                    result = element;
                } else {
                    dsl.annotation().error(NAME_SELECTION, "Selector method is ambiguous");
                    return null;
                }
            }
        }

        if (result == null) {
            dsl.annotation().error(NAME_SELECTION, "Selector method is not found");
            return null;
        }
        return result;
    }

    private static void validateSelectorDeclaration(DslBuilder dsl, ExecutableElement selector) {
        assert selector != null;
        if (selector.getModifiers().contains(Modifier.PUBLIC) == false) {
            dsl.annotation().error(NAME_SELECTION, "Selector method must be \"public\"");
        }
        if (selector.getModifiers().contains(Modifier.ABSTRACT)) {
            dsl.annotation().error(NAME_SELECTION, "Selector method must not be \"abstract\"");
        }
        if (selector.getModifiers().contains(Modifier.STATIC)) {
            dsl.annotation().error(NAME_SELECTION, "Selector method must not be \"static\"");
        }
    }

    private static void checkParameters(
            CompileEnvironment environment,
            ExecutableElement operatorMethod,
            ExecutableElement selectorMethod) throws ResolveException {
        assert environment != null;
        assert operatorMethod != null;
        assert selectorMethod != null;
        assert operatorMethod.getParameters().isEmpty() == false;
        List<? extends VariableElement> operatorParams = operatorMethod.getParameters();
        List<? extends VariableElement> selectorParams = selectorMethod.getParameters();
        checkParameterCount(operatorMethod, selectorMethod);
        DataModelMirror operatorMaster = environment.findDataModel(operatorParams.get(0).asType());
        DataModelMirror selectorMaster = extractSelectorMaster(
                environment, selectorMethod, selectorParams.get(0).asType());
        if (isValidMaster(operatorMaster, selectorMaster) == false) {
            throw new ResolveException(MessageFormat.format(
                    "The first parameter in selector method \"{0}\" must be in form of List<{1}>",
                    selectorMethod.getSimpleName(),
                    operatorMaster));
        }
        if (selectorParams.size() == 1) {
            return;
        }
        DataModelMirror operatorTx = environment.findDataModel(operatorParams.get(1).asType());
        DataModelMirror selectorTx = environment.findDataModel(selectorParams.get(1).asType());
        if (isValidTx(operatorTx, selectorTx) == false) {
            throw new ResolveException(MessageFormat.format(
                    "The first parameter in selector method \"{0}\" must have super-type of {1}",
                    selectorMethod.getSimpleName(),
                    operatorTx));
        }
        DataModelMirror selectorResult = environment.findDataModel(selectorMethod.getReturnType());
        if (isValidResult(operatorMaster, selectorMaster, selectorResult) == false) {
            throw new ResolveException(MessageFormat.format(
                    "The return type of selector method \"{0}\" must be sub-type of {1}",
                    selectorMethod.getSimpleName(),
                    operatorMaster));
        }
        for (int i = 2, n = selectorParams.size(); i < n; i++) {
            TypeMirror expected = operatorParams.get(i).asType();
            TypeMirror actual = selectorParams.get(i).asType();
            if (environment.getProcessingEnvironment().getTypeUtils().isSubtype(expected, actual) == false) {
                throw new ResolveException(MessageFormat.format(
                        "The parameter \"{2}\" in selector method \"{0}\" must have super-type of {1}",
                        selectorMethod.getSimpleName(),
                        expected,
                        selectorParams.get(i)));
            }
        }
    }

    private static boolean isValidMaster(DataModelMirror operatorMaster, DataModelMirror selectorMaster) {
        if (operatorMaster == null || selectorMaster == null) {
            return false;
        }
        return operatorMaster.canContain(selectorMaster);
    }

    private static boolean isValidTx(DataModelMirror operatorTx, DataModelMirror selectorTx) {
        if (operatorTx == null || selectorTx == null) {
            return false;
        }
        return operatorTx.canInvoke(selectorTx);
    }

    private static boolean isValidResult(
            DataModelMirror operatorMaster,
            DataModelMirror selectorMaster,
            DataModelMirror selectorResult) {
        if (operatorMaster == null || selectorMaster == null || selectorResult == null) {
            return false;
        }
        if (selectorResult.canInvoke(operatorMaster)) {
            return true;
        }
        // FIXME restrict
        if (selectorMaster.getKind() == Kind.PARTIAL && selectorMaster.isSame(selectorResult)) {
            return true;
        }
        return false;
    }

    private static void checkParameterCount(
            ExecutableElement operatorMethod,
            ExecutableElement selectorMethod) throws ResolveException {
        assert operatorMethod != null;
        assert selectorMethod != null;
        List<? extends VariableElement> operatorParams = operatorMethod.getParameters();
        List<? extends VariableElement> selectorParams = selectorMethod.getParameters();
        if (operatorParams.size() < selectorParams.size()) {
            throw new ResolveException(MessageFormat.format(
                    "The selector method \"{0}\" can only have parameters less than this operator's",
                    selectorMethod.getSimpleName()));
        }
        if (selectorParams.size() == 0) {
            throw new ResolveException(MessageFormat.format(
                    "The first parameter in the selector method \"{0}\" must have a list of {1}",
                    selectorMethod.getSimpleName(),
                    operatorParams.get(0).asType()));
        }
    }

    private static DataModelMirror extractSelectorMaster(
            CompileEnvironment environment,
            ExecutableElement selectorMethod,
            TypeMirror firstParameter) throws ResolveException {
        assert environment != null;
        assert selectorMethod != null;
        assert firstParameter != null;
        TypeMirror erasedSelector = environment.getErasure(firstParameter);
        Types types = environment.getProcessingEnvironment().getTypeUtils();
        if (types.isSameType(erasedSelector, environment.findDeclaredType(TYPE_LIST)) == false) {
            throw new ResolveException(MessageFormat.format(
                    "The first parameter in the selector method \"{0}\" must be in form of List<...>",
                    selectorMethod.getSimpleName()));
        }
        DeclaredType list = (DeclaredType) firstParameter;
        if (list.getTypeArguments().size() != 1) {
            throw new ResolveException(MessageFormat.format(
                    "The first parameter in the selector method \"{0}\" must be in form of List<...>",
                    selectorMethod.getSimpleName()));
        }
        TypeMirror selectorElement = list.getTypeArguments().get(0);
        return environment.findDataModel(selectorElement);
    }

    private MasterKindOperatorHelper() {
        return;
    }

    static class ResolveException extends Exception {

        private static final long serialVersionUID = 1L;

        public ResolveException(String message) {
            super(message);
        }
    }
}
