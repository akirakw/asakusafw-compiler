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
import com.asakusafw.lang.compiler.operator.builtin.DslBuilder.KeyRef;
import com.asakusafw.lang.compiler.operator.builtin.DslBuilder.TypeRef;
import com.asakusafw.lang.compiler.operator.model.OperatorDescription;

/**
 * {@link OperatorDriver} for {@code CoGroup} annotation.
 */
public class CoGroupOperatorDriver extends AbstractOperatorDriver {

    private static final String INPUT_BUFFER = "inputBuffer"; //$NON-NLS-1$

    @Override
    public ClassDescription getAnnotationTypeName() {
        return Constants.getBuiltinOperatorClass("CoGroup"); //$NON-NLS-1$
    }

    @Override
    public OperatorDescription analyze(Context context) {
        DslBuilder dsl = new DslBuilder(context);
        if (dsl.method().modifiers().contains(Modifier.ABSTRACT)) {
            dsl.method().error("This operator method must not be \"abstract\"");
        }
        if (dsl.result().type().isVoid() == false) {
            dsl.method().error("This operator method must return \"void\"");
        }
        for (ElementRef p : dsl.parameters()) {
            TypeRef type = p.type();
            if (type.isList()) {
                TypeRef arg = type.arg(0);
                if (arg.isDataModel()) {
                    KeyRef key = p.resolveKey(arg);
                    dsl.addInput(p.document(), p.name(), arg.mirror(), key, p.reference());
                } else {
                    p.error("Input List element must be a data model type");
                }
            } else if (type.isResult()) {
                TypeRef arg = type.arg(0);
                if (arg.isDataModel()) {
                    dsl.addOutput(p.document(), p.name(), arg.mirror(), p.reference());
                } else {
                    p.error("Output Result element must be a data model type");
                }
            } else if (type.isBasic()) {
                dsl.consumeArgument(p);
            } else {
                p.error("This operator's parameters must be one of List, Result, or basic type");
            }
        }
        dsl.addAttribute(dsl.annotation().constant(INPUT_BUFFER));
        dsl.requireShuffle();
        return dsl.toDescription();
    }
}
