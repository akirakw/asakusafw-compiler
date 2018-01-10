/**
 * Copyright 2011-2018 Asakusa Framework Team.
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
package com.asakusafw.vanilla.core.io;

import java.io.IOException;
import java.nio.ByteBuffer;

import com.asakusafw.lang.utils.common.Arguments;
import com.asakusafw.lang.utils.common.InterruptibleIo;
import com.asakusafw.vanilla.core.util.Buffers;

/**
 * A zero-copy implementation of {@link RecordCursor} which can read contents written by {@link BasicRecordSink}.
 * @since 0.4.0
 */
public class DirectRecordCursor implements RecordCursor {

    private ByteBuffer buffer;

    private ByteBuffer view;

    private final InterruptibleIo resource;

    /**
     * Creates a new instance.
     * @param buffer the source buffer
     * @param resource the attached resource (nullable)
     */
    public DirectRecordCursor(ByteBuffer buffer, InterruptibleIo resource) {
        Arguments.requireNonNull(buffer);
        this.buffer = Buffers.duplicate(buffer);
        this.view = Buffers.duplicate(buffer);
        this.resource = resource;
    }

    @Override
    public boolean next() {
        ByteBuffer buf = buffer;
        if (buf == null) {
            return false;
        }
        int size = buf.getInt();
        if (size < 0) {
            release();
            return false;
        } else {
            int begin = buf.position();
            int end = buf.position() + size;
            buf.position(end);
            Buffers.range(view, begin, end);
            return true;
        }
    }

    @Override
    public ByteBuffer get() {
        return view;
    }

    @Override
    public void close() throws IOException, InterruptedException {
        release();
        if (resource != null) {
            resource.close();
        }
    }

    private void release() {
        buffer = null;
        view = null;
    }
}
