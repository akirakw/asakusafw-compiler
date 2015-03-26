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

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.lang.model.element.Modifier;

import com.asakusafw.lang.compiler.model.description.ClassDescription;
import com.asakusafw.lang.compiler.operator.AbstractOperatorDriver;
import com.asakusafw.lang.compiler.operator.Constants;
import com.asakusafw.lang.compiler.operator.OperatorDriver;
import com.asakusafw.lang.compiler.operator.builtin.DslBuilder.AnnotationRef;
import com.asakusafw.lang.compiler.operator.builtin.DslBuilder.ElementRef;
import com.asakusafw.lang.compiler.operator.builtin.DslBuilder.KeyRef;
import com.asakusafw.lang.compiler.operator.builtin.DslBuilder.TypeRef;
import com.asakusafw.lang.compiler.operator.model.OperatorDescription;
import com.asakusafw.lang.compiler.operator.model.OperatorDescription.Document;
import com.asakusafw.lang.compiler.operator.model.OperatorDescription.Node;

/**
 * {@link OperatorDriver} for {@code MasterJoin} annotation.
 */
public class MasterJoinOperatorDriver extends AbstractOperatorDriver {

    private static final String JOINED_PORT = "joinedPort"; //$NON-NLS-1$

    private static final String MISSED_PORT = "missedPort"; //$NON-NLS-1$

    @Override
    public ClassDescription getAnnotationTypeName() {
        return Constants.getBuiltinOperatorClass("MasterJoin"); //$NON-NLS-1$
    }

    @Override
    public OperatorDescription analyze(Context context) {
        DslBuilder dsl = new DslBuilder(context);
        if (dsl.method().modifiers().contains(Modifier.ABSTRACT) == false) {
            dsl.method().error("this operator method must be \"abstract\"");
        }
        if (dsl.result().type().isDataModel() == false) {
            dsl.method().error("this operator method must return a data model type");
        }
        if (dsl.isGeneric()) {
            dsl.method().error("this operator must not have any type parameters");
        }
        if (dsl.sawError()) {
            return null;
        }

        AnnotationRef joined = dsl.result().type().annotation(Constants.TYPE_JOINED);
        if (joined == null) {
            dsl.result().error("the return type must be a \"Joined data model\"");
            return null;
        }
        List<AnnotationRef> terms = joined.annotations("terms"); //$NON-NLS-1$
        if (terms == null || terms.isEmpty()) {
            dsl.result().error("the return type is invalid joind data model (\"terms\" is not declared?)");
            return null;
        }

        for (ElementRef p : dsl.parameters(0)) {
            TypeRef type = p.type();
            KeyRef key = null;
            if (type.isDataModel()) {
                boolean saw = false;
                for (Iterator<AnnotationRef> iter = terms.iterator(); iter.hasNext();) {
                    AnnotationRef term = iter.next();
                    TypeRef termType = term.type("source"); //$NON-NLS-1$
                    if (type.isEqualTo(termType)) {
                        iter.remove();
                        AnnotationRef shuffle = term.annotation("shuffle");
                        if (shuffle != null) {
                            key = p.resolveKey(type, shuffle.get());
                        }
                        saw = true;
                        break;
                    }
                }
                if (saw) {
                    dsl.addInput(p.document(), p.name(), type.mirror(), key, p.reference());
                } else {
                    p.error("this output type is not a valid join source");
                }
            } else if (type.isBasic()) {
                p.error("this operator cannot have any basic arguments");
            } else {
                p.error("rest of parameters must be Result type");
            }
        }
        if (dsl.getInputs().size() != 2) {
            dsl.method().error("this operator must have just 2 input data model parameters");
        }
        if (terms.isEmpty()) {
            List<Node> inputs = dsl.getInputs();
            assert inputs.isEmpty() == false;
            Node txInput = inputs.get(inputs.size() - 1);
            dsl.addOutput(
                    dsl.result().document(),
                    dsl.annotation().string(JOINED_PORT),
                    dsl.result().type().mirror(),
                    dsl.result().reference());
            dsl.addOutput(
                    Document.text("missed dataset"),
                    dsl.annotation().string(MISSED_PORT),
                    txInput.getType(),
                    txInput.getReference());
        } else {
            if (dsl.sawError() == false) {
                List<TypeRef> types = new ArrayList<>();
                for (AnnotationRef term : terms) {
                    TypeRef type = term.type("source"); //$NON-NLS-1$
                    types.add(type);
                }
                dsl.result().error(MessageFormat.format(
                        "some join source types do not appeared in parameter: {0}",
                        types));
            }
        }
        dsl.setSupport(MasterKindOperatorHelper.extractMasterSelection(dsl));
        dsl.requireShuffle();
        return dsl.toDescription();
    }
}
