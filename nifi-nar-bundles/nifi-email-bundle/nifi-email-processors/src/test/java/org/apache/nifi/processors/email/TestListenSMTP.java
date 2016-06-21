/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
*/
package org.apache.nifi.processors.email;

import org.apache.commons.mail.Email;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.SimpleEmail;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSessionFactory;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.ssl.StandardSSLContextService;
import org.apache.nifi.util.MockFlowFile;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;

import org.apache.nifi.ssl.SSLContextService;

import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class TestListenSMTP {

    @Test(timeout=10000)
    public void ValidEmailTls() throws Exception {


        final TestRunner runner = TestRunners.newTestRunner(ListenSMTP.class);
        runner.setProperty(ListenSMTP.SMTP_PORT, "0");
        runner.setProperty(ListenSMTP.SMTP_HOSTNAME, "bermudatriangle");
        runner.setProperty(ListenSMTP.SMTP_MAXIMUM_CONNECTIONS, "2");
        runner.setProperty(ListenSMTP.SMTP_TIMEOUT, "2000");

        // Setup the SSL Context
        final SSLContextService sslContextService = new StandardSSLContextService();
        runner.addControllerService("ssl-context", sslContextService);
        runner.setProperty(sslContextService, StandardSSLContextService.TRUSTSTORE, "src/test/resources/localhost-ts.jks");
        runner.setProperty(sslContextService, StandardSSLContextService.TRUSTSTORE_PASSWORD, "localtest");
        runner.setProperty(sslContextService, StandardSSLContextService.TRUSTSTORE_TYPE, "JKS");
        runner.setProperty(sslContextService, StandardSSLContextService.KEYSTORE, "src/test/resources/localhost-ks.jks");
        runner.setProperty(sslContextService, StandardSSLContextService.KEYSTORE_PASSWORD, "localtest");
        runner.setProperty(sslContextService, StandardSSLContextService.KEYSTORE_TYPE, "JKS");
        runner.enableControllerService(sslContextService);

        // and add the SSL context to the runner
        runner.setProperty(ListenSMTP.SSL_CONTEXT_SERVICE, "ssl-context");
        runner.setProperty(ListenSMTP.CLIENT_AUTH, SSLContextService.ClientAuth.NONE.name());



        final ProcessSessionFactory processSessionFactory = runner.getProcessSessionFactory();
        final ProcessContext context = runner.getProcessContext();

        runner.run(1, false);


        try {
            final Thread clientThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {


                        System.setProperty("mail.smtp.ssl.trust", "*");
                        System.setProperty("javax.net.ssl.keyStore", "src/test/resources/localhost-ks.jks");
                        System.setProperty("javax.net.ssl.keyStorePassword", "localtest");

                        final int port = ((ListenSMTP) runner.getProcessor()).getPort();

                        Email email = new SimpleEmail();

                        email.setHostName("127.0.0.1");
                        email.setSmtpPort(port);

                        // Enable STARTTLS but ignore the cert
                        email.setStartTLSEnabled(true);
                        email.setStartTLSRequired(true);
                        email.setSSLCheckServerIdentity(false);

                        email.setFrom("alice@nifi.apache.org");
                        email.setSubject("This is a test");
                        email.setMsg("Test test test chocolate");
                        email.addTo("bob@nifi.apache.org");

                        email.send();

                    } catch (final Throwable t) {
                        t.printStackTrace();
                        Assert.fail(t.toString());
                    }
                }
            });
            clientThread.start();

            while (runner.getFlowFilesForRelationship(ListenSMTP.REL_SUCCESS).isEmpty()) {
                // process the request.
                runner.run(1, false, false);
            }

            runner.assertTransferCount(ListenSMTP.REL_SUCCESS, 1);

            runner.assertQueueEmpty();
            final List<MockFlowFile> splits = runner.getFlowFilesForRelationship(ListenSMTP.REL_SUCCESS);
            splits.get(0).assertAttributeEquals("smtp.from", "alice@nifi.apache.org");
            splits.get(0).assertAttributeEquals("smtp.to", "bob@nifi.apache.org");

        } finally {
            // shut down the server
            runner.run(1, true);
        }
    }

    @Test(timeout=10000)
    public void ValidEmail() throws Exception {
        final TestRunner runner = TestRunners.newTestRunner(ListenSMTP.class);
        runner.setProperty(ListenSMTP.SMTP_PORT, "0");
        runner.setProperty(ListenSMTP.SMTP_HOSTNAME, "bermudatriangle");
        runner.setProperty(ListenSMTP.SMTP_MAXIMUM_CONNECTIONS, "2");
        runner.setProperty(ListenSMTP.SMTP_TIMEOUT, "2000");

        final ProcessSessionFactory processSessionFactory = runner.getProcessSessionFactory();
        final ProcessContext context = runner.getProcessContext();

        runner.run(1, false);


        try {
            final Thread clientThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        final int port = ((ListenSMTP) runner.getProcessor()).getPort();

                        Email email = new SimpleEmail();
                        email.setHostName("127.0.0.1");
                        email.setSmtpPort(port);
                        email.setStartTLSEnabled(false);
                        email.setFrom("alice@nifi.apache.org");
                        email.setSubject("This is a test");
                        email.setMsg("Test test test chocolate");
                        email.addTo("bob@nifi.apache.org");
                        email.send();

                    } catch (final Throwable t) {
                        t.printStackTrace();
                        Assert.fail(t.toString());
                    }
                }
            });
            clientThread.start();

            while (runner.getFlowFilesForRelationship(ListenSMTP.REL_SUCCESS).isEmpty()) {
                // process the request.
                runner.run(1, false, false);
            }

            runner.assertTransferCount(ListenSMTP.REL_SUCCESS, 1);

            runner.assertQueueEmpty();
            final List<MockFlowFile> splits = runner.getFlowFilesForRelationship(ListenSMTP.REL_SUCCESS);
            splits.get(0).assertAttributeEquals("smtp.from", "alice@nifi.apache.org");
            splits.get(0).assertAttributeEquals("smtp.to", "bob@nifi.apache.org");

        } finally {
                // shut down the server
                runner.run(1, true);
            }
    }

    @Test(timeout=10000)
    public void emailTooLarge() throws Exception {
        final TestRunner runner = TestRunners.newTestRunner(ListenSMTP.class);
        runner.setProperty(ListenSMTP.SMTP_PORT, "0");
        runner.setProperty(ListenSMTP.SMTP_HOSTNAME, "bermudatriangle");
        runner.setProperty(ListenSMTP.SMTP_MAXIMUM_MSG_SIZE, "40B");
        runner.setProperty(ListenSMTP.SMTP_MAXIMUM_CONNECTIONS, "2");
        runner.setProperty(ListenSMTP.SMTP_TIMEOUT, "2000");

        final ProcessSessionFactory processSessionFactory = runner.getProcessSessionFactory();
        final ProcessContext context = runner.getProcessContext();

        final boolean[] failed = {false};

        runner.run(1, false);


        try {
            final Thread clientThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        final int port = ((ListenSMTP) runner.getProcessor()).getPort();

                        Email email = new SimpleEmail();
                        email.setHostName("127.0.0.1");
                        email.setSmtpPort(port);
                        email.setStartTLSEnabled(false);
                        email.setFrom("alice@nifi.apache.org");
                        email.setSubject("This is a test");
                        email.setMsg("Test test test chocolate");
                        email.addTo("bob@nifi.apache.org");
                        email.send();

                    } catch (EmailException t) {
                        Assert.assertTrue(true);
                        failed[0] = true;
                    }
                }
            });
            clientThread.start();

            while (failed[0] != true) {
                // process the request.
                runner.run(1, false, false);
            }
            runner.assertTransferCount(ListenSMTP.REL_SUCCESS, 0);
            runner.assertQueueEmpty();

        } catch (ProcessException t) {
            Assert.assertTrue(true);
        } finally {
            // shut down the server
            runner.run(1, true);
        }


    }


}