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

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.processing.Completion;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.asakusafw.lang.compiler.model.description.ClassDescription;
import com.asakusafw.lang.compiler.operator.AbstractOperatorAnnotationProcessor;
import com.asakusafw.lang.compiler.operator.CompileEnvironment;
import com.asakusafw.lang.compiler.operator.OperatorDriver;
import com.asakusafw.lang.compiler.operator.model.OperatorClass;

/**
 * Processes Asakusa Operator Annotations.
 */
public class OperatorAnnotationProcessor extends AbstractOperatorAnnotationProcessor {

    static final Logger LOG = LoggerFactory.getLogger(OperatorAnnotationProcessor.class);

    @Override
    protected CompileEnvironment createCompileEnvironment(ProcessingEnvironment processingEnv) {
        return CompileEnvironment.newInstance(
                processingEnv,
                CompileEnvironment.Support.DATA_MODEL_REPOSITORY,
                CompileEnvironment.Support.OPERATOR_DRIVER,
                CompileEnvironment.Support.STRICT_CHECKING,
                CompileEnvironment.Support.FORCE_GENERATE_IMPLEMENTATION);
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        if (environment == null) {
            return Collections.singleton("*");
        }
        Set<String> results = new HashSet<>();
        for (OperatorDriver driver : environment.getOperatorDrivers()) {
            ClassDescription annotationType = driver.getAnnotationTypeName();
            results.add(annotationType.getClassName());
        }
        return results;
    }

    @Override
    public Iterable<? extends Completion> getCompletions(
            Element element,
            AnnotationMirror annotation,
            ExecutableElement member,
            String userText) {
        if (environment == null) {
            return Collections.emptySet();
        }
        if (element.getKind() != ElementKind.METHOD) {
            return Collections.emptyList();
        }
        OperatorDriver driver = this.environment.findDriver((TypeElement) annotation.getAnnotationType().asElement());
        if (driver == null) {
            return Collections.emptyList();
        } else {
            ExecutableElement method = (ExecutableElement) element;
            OperatorDriver.Context context = new OperatorDriver.Context(environment, annotation, method);
            return driver.getCompletions(context, member, userText);
        }
    }

    @Override
    protected void run(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        assert annotations != null;
        assert roundEnv != null;
        OperatorMethodAnalyzer analyzer = new OperatorMethodAnalyzer(environment);
        for (TypeElement annotation : annotations) {
            Set<ExecutableElement> methods = ElementFilter.methodsIn(roundEnv.getElementsAnnotatedWith(annotation));
            for (ExecutableElement method : methods) {
                analyzer.register(annotation, method);
            }
        }
        Collection<OperatorClass> operatorClasses = analyzer.resolve();
        LOG.debug("found {} operator classes", operatorClasses.size()); //$NON-NLS-1$
        OperatorFactoryEmitter factoryEmitter = new OperatorFactoryEmitter(environment);
        OperatorImplementationEmitter implementationEmitter = new OperatorImplementationEmitter(environment);
        for (OperatorClass aClass : operatorClasses) {
            LOG.debug("emitting support class: {}", aClass.getDeclaration().getQualifiedName()); //$NON-NLS-1$
            factoryEmitter.emit(aClass);
            if (environment.isForceGenerateImplementation()
                    || aClass.getDeclaration().getModifiers().contains(Modifier.ABSTRACT)) {
                implementationEmitter.emit(aClass);
            }
        }
    }
}
