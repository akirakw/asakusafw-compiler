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

import javax.lang.model.element.Modifier;

import com.asakusafw.lang.compiler.model.description.ClassDescription;
import com.asakusafw.lang.compiler.operator.AbstractOperatorDriver;
import com.asakusafw.lang.compiler.operator.Constants;
import com.asakusafw.lang.compiler.operator.OperatorDriver;
import com.asakusafw.lang.compiler.operator.builtin.DslBuilder.ElementRef;
import com.asakusafw.lang.compiler.operator.model.OperatorDescription;
import com.asakusafw.lang.compiler.operator.model.OperatorDescription.Document;
import com.asakusafw.lang.compiler.operator.model.OperatorDescription.Node;
import com.asakusafw.lang.compiler.operator.model.OperatorDescription.Reference;

/**
 * {@link OperatorDriver} for {@code MasterCheck} annotation.
 */
public class MasterCheckOperatorDriver extends AbstractOperatorDriver {

    private static final String JOINED_PORT = "foundPort"; //$NON-NLS-1$

    private static final String MISSED_PORT = "missedPort"; //$NON-NLS-1$

    @Override
    public ClassDescription getAnnotationTypeName() {
        return Constants.getBuiltinOperatorClass("MasterCheck"); //$NON-NLS-1$
    }

    @Override
    public OperatorDescription analyze(Context context) {
        DslBuilder dsl = new DslBuilder(context);
        if (dsl.method().modifiers().contains(Modifier.ABSTRACT) == false) {
            dsl.method().error("This operator method must be \"abstract\"");
        }
        if (dsl.result().type().isBoolean() == false) {
            dsl.method().error("This operator method must return boolean type");
        }
        MasterKindOperatorHelper.consumeMaster(dsl);
        MasterKindOperatorHelper.consumeTx(dsl);
        for (ElementRef p : dsl.parameters(2)) {
            p.error("This operator cannot have any basic parameters");
        }
        if (dsl.getInputs().isEmpty() == false) {
            Node txInput = dsl.getInputs().get(dsl.getInputs().size() - 1);
            dsl.addOutput(
                    Document.text("dataset for found master data"),
                    dsl.annotation().string(JOINED_PORT),
                    txInput.getType(),
                    txInput.getReference());
            dsl.addOutput(
                    Document.text("dataset for missed master data"),
                    dsl.annotation().string(MISSED_PORT),
                    txInput.getType(),
                    Reference.special(String.valueOf(false)));
        }
        dsl.setSupport(MasterKindOperatorHelper.extractMasterSelection(dsl));
        dsl.requireShuffle();
        return dsl.toDescription();
    }
}
