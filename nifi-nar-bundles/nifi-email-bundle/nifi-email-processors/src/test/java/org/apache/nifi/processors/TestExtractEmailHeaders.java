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

import org.apache.nifi.util.MockFlowFile;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;


public class TestExtractEmailHeaders {
    private static final Path simpleEmail = Paths.get("src/test/resources/simple.eml");
    private static final Path withAttachment = Paths.get("src/test/resources/attachment-only.eml");

    @Test
    public void testValidEmailWithAttachments() throws Exception {
        final TestRunner runner = TestRunners.newTestRunner(new ExtractEmailHeaders());

        runner.enqueue(withAttachment);
        runner.run();

        runner.assertTransferCount(ExtractEmailHeaders.REL_SUCCESS, 1);
        runner.assertTransferCount(ExtractEmailHeaders.REL_FAILURE, 0);

        runner.assertQueueEmpty();
        final List<MockFlowFile> splits = runner.getFlowFilesForRelationship(ExtractEmailHeaders.REL_SUCCESS);
        splits.get(0).assertAttributeEquals("email.headers.from.0", "Siegfried GOESCHL <siegfried.goeschl@it20one.at>");
        splits.get(0).assertAttributeEquals("email.attachment_count", "1");
    }

    @Test
    public void testValidEmailWithoutAttachments() throws Exception {
        final TestRunner runner = TestRunners.newTestRunner(new ExtractEmailHeaders());
        runner.setProperty(ExtractEmailHeaders.CAPTURED_HEADERS, "MIME-Version");
        runner.enqueue(simpleEmail);
        runner.run();

        runner.assertTransferCount(ExtractEmailHeaders.REL_SUCCESS, 1);
        runner.assertTransferCount(ExtractEmailHeaders.REL_FAILURE, 0);


        runner.assertQueueEmpty();
        final List<MockFlowFile> splits = runner.getFlowFilesForRelationship(ExtractEmailHeaders.REL_SUCCESS);
        splits.get(0).assertAttributeEquals("email.headers.from.0", "\"test_from@apache.org\" <test_from@apache.org>");
        splits.get(0).assertAttributeEquals("email.attachment_count", "0");
        splits.get(0).assertAttributeExists("email.headers.mime-version");
    }

    @Test
    public void testInvalidEmail() throws Exception {
        final TestRunner runner = TestRunners.newTestRunner(new ExtractEmailHeaders());
        runner.enqueue("test test test chocolate".getBytes());
        runner.run();

        runner.assertTransferCount(ExtractEmailHeaders.REL_SUCCESS, 0);
        runner.assertTransferCount(ExtractEmailHeaders.REL_FAILURE, 1);
    }

}