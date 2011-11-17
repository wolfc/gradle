/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.file.archive.compression;

import org.gradle.api.tasks.bundling.Compression;
import org.gradle.api.tasks.bundling.Decompressor;

/**
 * by Szczepan Faber, created at: 11/17/11
 */
public class DecompressorFactory {

    public Decompressor decompressor(Compression compression) {
        switch(compression) {
            case BZIP2: return new Bzip2Decompressor();
            case GZIP:  return new GzipDecompressor();
            default:    return new NoOpDecompressor();
        }
    }

    public Decompressor decompressor(String extension) {
        Compression c = selectCompression(extension);
        return decompressor(c);
    }

    private Compression selectCompression(String extension) {
        Compression[] values = Compression.values();
        for (Compression c : values) {
            if (c.getExtension().equals(extension)) {
                return c;
            }
        }
        return Compression.NONE;
    }
}