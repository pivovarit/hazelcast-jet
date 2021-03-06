/*
 * Copyright (c) 2008-2019, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.jet.impl.util;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import javax.annotation.Nonnull;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static com.hazelcast.jet.impl.util.ExceptionUtil.sneakyThrow;

public final class IOUtil {

    private static final int BUFFER_SIZE = 1 << 14;

    private IOUtil() {
    }

    /**
     * Creates a ZIP-file stream from the directory tree rooted at the supplied
     * {@code baseDir}. Copies the stream into the provided output stream, closing
     * it when done.
     * <p>
     * <strong>Note:</strong> hidden files and directories are ignored
     */
    @SuppressFBWarnings(value = "RCN_REDUNDANT_NULLCHECK_WOULD_HAVE_BEEN_A_NPE",
            justification = "it's a false positive since java 11: https://github.com/spotbugs/spotbugs/issues/756")
    public static void packDirectoryIntoZip(@Nonnull Path baseDir, @Nonnull OutputStream destination)
            throws IOException {
        try (
                ZipOutputStream zipOut = new ZipOutputStream(destination);
                Stream<Path> fileStream = Files.walk(baseDir)
        ) {
            fileStream.forEach(p -> {
                try {
                    if (Files.isHidden(p) || p == baseDir) {
                        return;
                    }
                    String relativePath = baseDir.relativize(p).toString();
                    boolean directory = Files.isDirectory(p);
                    // slash has been added instead of File.seperator since ZipEntry.isDirectory is checking against it.
                    relativePath = directory ? relativePath + "/" : relativePath;
                    zipOut.putNextEntry(new ZipEntry(relativePath));
                    if (!directory) {
                        Files.copy(p, zipOut);
                    }
                    zipOut.closeEntry();
                } catch (IOException e) {
                    throw sneakyThrow(e);
                }
            });
        }
    }

    /**
     * Creates a ZIP-file stream from the supplied input stream. The input will
     * be stored in a single file in the created zip. The {@code destination}
     * stream will be closed.
     *
     * @param source the stream to copy from
     * @param destination the stream to write to
     * @param fileName the name of the file in the destination ZIP
     */
    @SuppressFBWarnings(value = "RCN_REDUNDANT_NULLCHECK_WOULD_HAVE_BEEN_A_NPE",
            justification = "it's a false positive since java 11: https://github.com/spotbugs/spotbugs/issues/756")
    public static void packStreamIntoZip(@Nonnull InputStream source, @Nonnull OutputStream destination,
                                         @Nonnull String fileName)
            throws IOException {
        try (
                ZipOutputStream dstZipStream = new ZipOutputStream(destination)
        ) {
            dstZipStream.putNextEntry(new ZipEntry(fileName));
            copyStream(source, dstZipStream);
        }
    }

    public static void copyStream(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[BUFFER_SIZE];
        for (int readCount; (readCount = in.read(buf)) > 0; ) {
            out.write(buf, 0, readCount);
        }
    }

    @Nonnull
    public static byte[] readFully(@Nonnull InputStream in) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] b = new byte[BUFFER_SIZE];
            for (int len; (len = in.read(b)) != -1; ) {
                out.write(b, 0, len);
            }
            return out.toByteArray();
        }
    }

    public static void unzip(InputStream is, Path targetDir) throws IOException {
        try (ZipInputStream zipIn = new ZipInputStream(is)) {
            for (ZipEntry ze; (ze = zipIn.getNextEntry()) != null; ) {
                Path resolvedPath = targetDir.resolve(ze.getName());
                if (ze.isDirectory()) {
                    Files.createDirectories(resolvedPath);
                } else {
                    Path dir = resolvedPath.getParent();
                    assert dir != null : "null parent: " + resolvedPath;
                    Files.createDirectories(dir);
                    Files.copy(zipIn, resolvedPath);
                }
            }
        }
    }
}
