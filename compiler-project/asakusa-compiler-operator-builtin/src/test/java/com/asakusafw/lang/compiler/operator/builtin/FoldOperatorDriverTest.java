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

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.util.Map;

import javax.lang.model.type.TypeKind;

import org.junit.Test;

import com.asakusafw.lang.compiler.model.description.Descriptions;
import com.asakusafw.lang.compiler.operator.model.OperatorDescription;
import com.asakusafw.lang.compiler.operator.model.OperatorDescription.Node;
import com.asakusafw.lang.compiler.operator.model.OperatorElement;
import com.asakusafw.vocabulary.operator.Fold;

/**
 * Test for {@link FoldOperatorDriver}.
 */
public class FoldOperatorDriverTest extends OperatorDriverTestRoot {

    /**
     * Creates a new instance.
     */
    public FoldOperatorDriverTest() {
        super(new FoldOperatorDriver());
    }

    /**
     * annotation.
     */
    @Test
    public void annotationTypeName() {
        assertThat(driver.getAnnotationTypeName(), is(Descriptions.classOf(Fold.class)));
    }

    /**
     * Simple testing.
     */
    @Test
    public void simple() {
        compile(new Action("com.example.Simple") {
            @Override
            protected void perform(OperatorElement target) {
                OperatorDescription description = target.getDescription();
                assertThat(description.getInputs().size(), is(1));
                assertThat(description.getOutputs().size(), is(1));
                assertThat(description.getArguments().size(), is(0));

                Node input = description.getInputs().get(0);
                assertThat(input.getType(), is(sameType("com.example.Model")));

                Node output = description.getOutputs().get(0);
                assertThat(output.getName(), is(defaultName(Fold.class, "outputPort")));
                assertThat(output.getType(), is(sameType("com.example.Model")));
            }
        });
    }

    /**
     * With arguments.
     */
    @Test
    public void with_argument() {
        compile(new Action("com.example.WithArgument") {
            @Override
            protected void perform(OperatorElement target) {
                OperatorDescription description = target.getDescription();
                assertThat(description.getInputs().size(), is(1));
                assertThat(description.getOutputs().size(), is(1));
                assertThat(description.getArguments().size(), is(2));

                Node input = description.getInputs().get(0);
                assertThat(input.getType(), is(sameType("com.example.Model")));

                Node output = description.getOutputs().get(0);
                assertThat(output.getName(), is(defaultName(Fold.class, "outputPort")));
                assertThat(output.getType(), is(sameType("com.example.Model")));

                Map<String, Node> arguments = toMap(description.getArguments());
                assertThat(arguments.get("stringArg"), is(notNullValue()));
                assertThat(arguments.get("stringArg").getType(), is(sameType(String.class)));
                assertThat(arguments.get("intArg"), is(notNullValue()));
                assertThat(arguments.get("intArg").getType(), is(kindOf(TypeKind.INT)));
            }
        });
    }

    /**
     * Violates method is not abstract.
     */
    @Test
    public void violate_not_abstract() {
        violate("com.example.ViolateNotAbstract");
    }

    /**
     * Violates method returns enum.
     */
    @Test
    public void violate_return_void() {
        violate("com.example.ViolateReturnVoid");
    }

    /**
     * Violates method first parameter must be a model.
     */
    @Test
    public void violate_input_with_model1() {
        violate("com.example.ViolateInputWithModel1");
    }

    /**
     * Violates method second parameter must be a model.
     */
    @Test
    public void violate_input_with_model2() {
        violate("com.example.ViolateInputWithModel2");
    }

    /**
     * Violates method both inputs must be same type.
     */
    @Test
    public void violate_input_same_type() {
        violate("com.example.ViolateInputSameType");
    }

    /**
     * Violates method has just twin input.
     */
    @Test
    public void violate_input_with_key() {
        violate("com.example.ViolateInputWithKey");
    }

    /**
     * Violates method has just twin input.
     */
    @Test
    public void violate_twin_input1() {
        violate("com.example.ViolateTwinInput1");
    }

    /**
     * Violates method has just twin input.
     */
    @Test
    public void violate_twin_input3() {
        violate("com.example.ViolateTwinInput3");
    }

    /**
     * Violates method has only valid parameters.
     */
    @Test
    public void violate_valid_parameter() {
        violate("com.example.ViolateValidParameter");
    }
}
