/**
 * Copyright 2011-2017 Asakusa Framework Team.
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
package com.asakusafw.lang.info.operator;

import java.util.Arrays;

import org.junit.Test;

import com.asakusafw.lang.info.Attribute;
import com.asakusafw.lang.info.InfoSerDe;
import com.asakusafw.lang.info.operator.CoreOperatorSpec.CoreOperatorKind;
import com.asakusafw.lang.info.value.ClassInfo;
import com.asakusafw.lang.info.value.ValueInfo;

/**
 * Test for {@link OperatorAttribute}.
 */
public class OperatorAttributeTest {

    /**
     * simple case.
     */
    @Test
    public void simple() {
        InfoSerDe.checkRestore(
                Attribute.class,
                new OperatorAttribute(
                        CoreOperatorSpec.of(CoreOperatorKind.CHECKPOINT),
                        Arrays.asList(new ParameterInfo(
                                "a",
                                ClassInfo.of("int"),
                                ValueInfo.of(100)))));
    }
}
