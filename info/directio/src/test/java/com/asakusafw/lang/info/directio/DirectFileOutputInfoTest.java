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
package com.asakusafw.lang.info.directio;

import java.util.Arrays;

import org.junit.Test;

import com.asakusafw.lang.info.InfoSerDe;

/**
 * Test for {@link DirectFileOutputInfo}.
 */
public class DirectFileOutputInfoTest {

    /**
     * ser/de.
     */
    @Test
    public void serde() {
        InfoSerDe.checkRestore(
                DirectFileOutputInfo.class,
                new DirectFileOutputInfo(
                        "testing",
                        "com.example.OutputDesc",
                        "base/path",
                        "resource/pattern/*.bin",
                        "com.example.DataModel",
                        "com.example.DataFormat",
                        Arrays.asList("+x", "-y"),
                        Arrays.asList("delete/pattern/*.bin")));
    }
}
