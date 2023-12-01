/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.Utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;

public class UpcallTestHelper extends NativeTestHelper {
    public record Output(int result, List<String> stdout, List<String> stderr) {
        private static void assertContains(List<String> lines, String shouldInclude, String name) {
            assertTrue(lines.stream().anyMatch(line -> line.contains(shouldInclude)),
                "Did not find '" + shouldInclude + "' in " + name);
        }

        public Output assertFailed() {
            assertNotEquals(result, 0);
            return this;
        }

        public Output assertSuccess() {
            assertEquals(result, 0);
            return this;
        }

        public Output assertStdErrContains(String shouldInclude) {
            assertContains(stderr, shouldInclude, "stderr");
            return this;
        }

        public Output assertStdOutContains(String shouldInclude) {
            assertContains(stdout, shouldInclude, "stdout");
            return this;
        }
    }

    public Output runInNewProcess(Class<?> target, boolean useSpec, String... programArgs) throws IOException, InterruptedException {
        return runInNewProcess(target, useSpec, List.of(), List.of(programArgs));
    }

    public Output runInNewProcess(Class<?> target, boolean useSpec, List<String> vmArgs, List<String> programArgs) throws IOException, InterruptedException {
        assert !target.isArray();

        List<String> command = new ArrayList<>(List.of(
            "--enable-native-access=ALL-UNNAMED",
            "-Djava.library.path=" + System.getProperty("java.library.path"),
            "-Djdk.internal.foreign.UpcallLinker.USE_SPEC=" + useSpec
        ));
        command.addAll(vmArgs);
        command.add(target.getName());
        command.addAll(programArgs);

        Process process = ProcessTools.createTestJavaProcessBuilder(command).start();

        long timeOut = (long) (Utils.TIMEOUT_FACTOR * 1L);
        boolean completed = process.waitFor(timeOut, TimeUnit.MINUTES);
        assertTrue(completed, "Time out while waiting for process");

        List<String> outLines = linesFromStream(process.getInputStream());
        outLines.forEach(System.out::println);
        List<String> errLines = linesFromStream(process.getErrorStream());
        errLines.forEach(System.err::println);

        return new Output(process.exitValue(), outLines, errLines);
    }

    private static List<String> linesFromStream(InputStream stream) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
            return reader.lines().toList();
        }
    }
}
