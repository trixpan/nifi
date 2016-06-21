/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.nifi.processors;

import org.apache.nifi.processors.email.ExtractEmailAttachments;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

public class TestExtractEmailAttachments {
    private static final Path simpleEmail = Paths.get("src/test/resources/simple.eml");
    private static final Path withAttachment = Paths.get("src/test/resources/attachment-only.eml");

    @Test
    public void testValidEmailWithAttachments() throws Exception {
        final TestRunner runner = TestRunners.newTestRunner(new ExtractEmailAttachments());

        runner.enqueue(withAttachment);
        runner.run();

        runner.assertTransferCount(ExtractEmailAttachments.REL_ORIGINAL, 1);
        runner.assertTransferCount(ExtractEmailAttachments.REL_FAILURE, 0);
        runner.assertTransferCount(ExtractEmailAttachments.REL_ATTACHMENTS, 1);
    }

    @Test
    public void testValidEmailWithoutAttachments() throws Exception {
        final TestRunner runner = TestRunners.newTestRunner(new ExtractEmailAttachments());
        runner.enqueue(simpleEmail);
        runner.run();

        runner.assertTransferCount(ExtractEmailAttachments.REL_ORIGINAL, 1);
        runner.assertTransferCount(ExtractEmailAttachments.REL_FAILURE, 0);
        runner.assertTransferCount(ExtractEmailAttachments.REL_ATTACHMENTS, 0);

    }

    @Test
    public void testInvalidEmail() throws Exception {
        final TestRunner runner = TestRunners.newTestRunner(new ExtractEmailAttachments());
        runner.enqueue("test test test chocolate".getBytes());
        runner.run();

        runner.assertTransferCount(ExtractEmailAttachments.REL_ORIGINAL, 0);
        runner.assertTransferCount(ExtractEmailAttachments.REL_FAILURE, 1);
        runner.assertTransferCount(ExtractEmailAttachments.REL_ATTACHMENTS, 0);
    }
}