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
package org.apache.nifi.processors.cybersecurity;

import org.apache.nifi.util.MockFlowFile;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;

public class TestListTaxii {

    private TestRunner runner;
    @Before
    public void init() {
        runner = TestRunners.newTestRunner(ListTaxii.class);
    }

    @After
    public void stop() {
        runner.shutdown();
    }

    @Test
    public void TestPollListingInvalidURL() {

        runner.setProperty(ListTaxii.SERVER_URI, "https://doesntexist.example.com/read-only/services/collection-management");

        HashMap<String, String> attributes = new HashMap<>();

        runner.enqueue("test test test chocolate", attributes);
        runner.run();

        runner.assertQueueEmpty();
        runner.assertAllFlowFilesTransferred(ListTaxii.REL_FAILURE, 1);

        final MockFlowFile outFile = runner.getFlowFilesForRelationship(ListTaxii.REL_FAILURE).get(0);

        outFile.assertContentEquals("test test test chocolate");
    }

    @Ignore ("Created for illustration purposes. Adjust the proxy address in order to validate")
    @Test
    public void TestPollListingWithInvalidProxyConfiguration() {

        runner.setProperty(ListTaxii.SERVER_URI, "https://test.taxiistand.com/read-only/services/collection-management");
        runner.setProperty(ListTaxii.PROXY_HOST, "127.0.0.1");
        // Using TCP port 9 as it is discard by default
        runner.setProperty(ListTaxii.PROXY_PORT, "9");

        HashMap<String, String> attributes = new HashMap<>();

        runner.enqueue("test test test chocolate", attributes);
        runner.run();

        runner.assertQueueEmpty();
        runner.assertAllFlowFilesTransferred(ListTaxii.REL_FAILURE, 1);

        final MockFlowFile outFile = runner.getFlowFilesForRelationship(ListTaxii.REL_FAILURE).get(0);

        outFile.assertContentEquals("test test test chocolate");
    }

    @Ignore ("Created for illustration purposes. Adjust the proxy address in order to validate")
    @Test
    public void TestPollListingWithProxy() {

        runner.setProperty(ListTaxii.SERVER_URI, "https://test.taxiistand.com/read-only/services/collection-management");
        runner.setProperty(ListTaxii.PROXY_HOST, "127.0.0.1");
        runner.setProperty(ListTaxii.PROXY_PORT, "8080");

        HashMap<String, String> attributes = new HashMap<>();

        runner.enqueue("test test test chocolate", attributes);
        runner.run();

        runner.assertQueueEmpty();

        final List<MockFlowFile> failedFlowFiles = runner.getFlowFilesForRelationship(ListTaxii.REL_FAILURE);
        final List<MockFlowFile> successFlowFiles = runner.getFlowFilesForRelationship(ListTaxii.REL_SUCCESS);

        Assert.assertTrue(failedFlowFiles.size() == 0);
        Assert.assertTrue(successFlowFiles.size() > 0);

        for (MockFlowFile flowFile : successFlowFiles) {
            flowFile.assertAttributeExists("taxii.collection_type");
            flowFile.assertAttributeExists("taxii.collection_name");
            flowFile.assertAttributeExists("taxii.collection_volume");
        }
    }

    @Ignore ("Created for illustration purposes. Adjust the proxy address in order to validate")
    @Test
    public void TestPollListingWithWrongPassword() {

        runner.setProperty(ListTaxii.SERVER_URI, "https://test.taxiistand.com/read-write-auth/services/collection-management");
        runner.setProperty(ListTaxii.SERVER_USERNAME, "guest");
        runner.setProperty(ListTaxii.SERVER_PASSWORD, "wrong password");
        runner.setProperty(ListTaxii.PROXY_HOST, "127.0.0.1");
        runner.setProperty(ListTaxii.PROXY_PORT, "8080");

        HashMap<String, String> attributes = new HashMap<>();

        runner.enqueue("test test test chocolate", attributes);
        runner.run();

        runner.assertQueueEmpty();
        runner.assertAllFlowFilesTransferred(ListTaxii.REL_FAILURE, 1);

        final MockFlowFile outFile = runner.getFlowFilesForRelationship(ListTaxii.REL_FAILURE).get(0);

        outFile.assertAttributeExists("taxii.status_type");

        outFile.assertAttributeEquals("taxii.status_type", "UNAUTHORIZED");
    }
}