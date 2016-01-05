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

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;

import com.asakusafw.lang.compiler.model.description.ClassDescription;
import com.asakusafw.lang.compiler.operator.Callback;
import com.asakusafw.lang.compiler.operator.CompileEnvironment;
import com.asakusafw.lang.compiler.operator.CompileEnvironment.Support;
import com.asakusafw.lang.compiler.operator.MockDataModelMirrorRepository;
import com.asakusafw.lang.compiler.operator.OperatorCompilerTestRoot;
import com.asakusafw.lang.compiler.operator.OperatorDriver;
import com.asakusafw.lang.compiler.operator.method.OperatorMethodAnalyzer;
import com.asakusafw.lang.compiler.operator.model.OperatorClass;
import com.asakusafw.lang.compiler.operator.model.OperatorDescription.Node;
import com.asakusafw.lang.compiler.operator.model.OperatorElement;

/**
 * Test helper for {@link OperatorDriver}s.
 */
public class OperatorDriverTestRoot extends OperatorCompilerTestRoot {

    /**
     * Target operator driver.
     */
    protected final OperatorDriver driver;

    final List<String> dataModelNames;

    /**
     * Creates a new instance.
     * @param driver target driver
     * @throws IllegalArgumentException if some parameters were {@code null}
     */
    public OperatorDriverTestRoot(OperatorDriver driver) {
        this.driver = driver;
        this.dataModelNames = new ArrayList<>();
    }

    /**
     * Returns the default name.
     * @param annotationType the target operator annotation
     * @param elementName the target element name
     * @return the default name
     */
    protected static String defaultName(Class<? extends Annotation> annotationType, String elementName) {
        try {
            Method element = annotationType.getMethod(elementName);
            return (String) element.getDefaultValue();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    /**
     * Compiles and invoke the action.
     * @param action target test action
     */
    protected void compile(Action action) {
        prepare(action.className, action.supportClassNames);
        start(action);
        assertThat(action.performed, is(true));
    }

    /**
     * Compiles and check violation exist.
     * @param className target class name
     * @param supportClassNames support class names
     * @throws IllegalArgumentException if some parameters were {@code null}
     */
    protected void violate(String className, String... supportClassNames) {
        prepare(className, Arrays.asList(supportClassNames));
        Action action = new Action(className) {
            @Override
            protected void perform(OperatorElement target) {
                return;
            }
        };
        action.expectError = true;
        error(action);
        assertThat(action.performed, is(true));
    }

    private void prepare(String className, List<String> supportClassNames) {
        add(className);
        for (String name : supportClassNames) {
            add(name);
        }
        addModels();
    }

    /**
     * Registers data model types.
     */
    protected void addModels() {
        addDataModel("Model", "package com.example; public class Model { public int key; public String content; }");
        addDataModel("Side", "package com.example; public class Side { public int id; }");
        addDataModel("Proceeded", "package com.example; public class Proceeded { public int proceeded; }");
        addDataModel("Projective", "package com.example; public class Projective { public int content; }");
    }

    /**
     * Registers data model type.
     * @param simpleName target simple name (this will be qualified with {@code com.example})
     */
    protected final void addDataModel(String simpleName) {
        add("com/example/" + simpleName);
        dataModelNames.add(simpleName);
    }

    /**
     * Registers data model type.
     * @param simpleName target simple name (this will be qualified with {@code com.example})
     * @param content target content
     */
    protected final void addDataModel(String simpleName, String content) {
        add("com/example/" + simpleName, content);
        dataModelNames.add(simpleName);
    }

    /**
     * Test action.
     */
    protected abstract class Action extends Callback {

        final String className;

        final List<String> supportClassNames;

        boolean performed;

        boolean expectError;

        /**
         * Creates a new instance.
         * @param className target class name
         * @param supportClassNames support class names
         */
        protected Action(String className, String... supportClassNames) {
            this.className = className;
            this.supportClassNames = Arrays.asList(supportClassNames);
        }

        @Override
        protected CompileEnvironment createCompileEnvironment(ProcessingEnvironment processingEnv) {
            return new CompileEnvironment(
                    processingEnv,
                    Arrays.asList(driver),
                    Arrays.asList(new MockDataModelMirrorRepository()
                            .add("com.example", dataModelNames.toArray(new String[dataModelNames.size()]))));
        }

        @Override
        protected void test() {
            TypeElement element = env.findTypeElement(new ClassDescription(className));
            assertThat(className, element, is(notNullValue()));
            while (element.getEnclosingElement().getKind() != ElementKind.PACKAGE) {
                element = (TypeElement) element.getEnclosingElement();
            }
            if (round.getRootElements().contains(element)) {
                this.performed = true;
                testSpi();
                OperatorMethodAnalyzer analyzer = new OperatorMethodAnalyzer(env);
                for (TypeElement annotation : annotatios) {
                    if (env.findDriver(annotation) == null) {
                        continue;
                    }
                    for (ExecutableElement method : ElementFilter.methodsIn(round.getElementsAnnotatedWith(annotation))) {
                        analyzer.register(annotation, method);
                    }
                }
                Collection<OperatorClass> resolved = analyzer.resolve();
                if (expectError) {
                    return;
                }
                if (resolved.size() == 1) {
                    OperatorClass first = resolved.iterator().next();
                    assertThat(first.getDeclaration(), is(element));
                    for (OperatorElement op : first.getElements()) {
                        assertThat(op.getDeclaration().toString(), op.getDescription(), is(notNullValue()));
                        perform(op);
                    }
                } else {
                    perform(null);
                }
            }
        }

        private void testSpi() {
            CompileEnvironment spi = CompileEnvironment.newInstance(
                    env.getProcessingEnvironment(),
                    Support.OPERATOR_DRIVER);
            OperatorDriver loaded = spi.findDriver(spi.findTypeElement(driver.getAnnotationTypeName()));
            assertThat(className, loaded, is(notNullValue()));
        }

        /**
         * Performs the test.
         * @param target test target
         */
        protected abstract void perform(OperatorElement target);

        /**
         * Converts node list into {@code name -> node} map.
         * @param nodes node list
         * @return converted map
         */
        protected Map<String, Node> toMap(List<Node> nodes) {
            Map<String, Node> results = new HashMap<>();
            for (Node node : nodes) {
                results.put(node.getName(), node);
            }
            return results;
        }
    }
}
