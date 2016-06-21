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

package org.apache.nifi.processors.email.smtp.handler;

import java.io.IOException;
import java.io.InputStream;
import java.security.cert.X509Certificate;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.commons.io.IOUtils;

import org.subethamail.smtp.MessageContext;
import org.subethamail.smtp.MessageHandler;
import org.subethamail.smtp.MessageHandlerFactory;
import org.subethamail.smtp.RejectException;
import org.subethamail.smtp.TooMuchDataException;
import org.subethamail.smtp.server.SMTPServer;

import org.apache.nifi.processors.email.smtp.event.SmtpEvent;


public class SMTPMessageHandlerFactory implements MessageHandlerFactory {
    final LinkedBlockingQueue<SmtpEvent> messages;

    public SMTPMessageHandlerFactory(LinkedBlockingQueue<SmtpEvent> messages) {
        this.messages = messages;
    }

    @Override
    public MessageHandler create(MessageContext messageContext) {
        return new Handler(messageContext, messages);
    }

    class Handler implements MessageHandler {
        final MessageContext messageContext;
        String from;
        String recipient;
        byte [] messageBody;


        boolean failed;

        public Handler(MessageContext messageContext, LinkedBlockingQueue<SmtpEvent> messages){
            this.messageContext = messageContext;
        }

        @Override
        public void from(String from) throws RejectException {
            // TODO: possibly whitelist senders?
            this.from = from;
        }

        @Override
        public void recipient(String recipient) throws RejectException {
            // TODO: possibly whitelist receivers?
            this.recipient = recipient;
        }

        @Override
        public void data(InputStream inputStream) throws RejectException, TooMuchDataException, IOException {
            SMTPServer server = messageContext.getSMTPServer();

            if  (inputStream.available() > server.getMaxMessageSize()) {
                failed = true;
                throw new TooMuchDataException("Data exceeds the amount allowed.");
            } else {
                this.messageBody = IOUtils.toByteArray(inputStream);
            }
        }

        @Override
        public void done() {

            X509Certificate[] certificates = new X509Certificate[]{};

            String remoteIP = messageContext.getRemoteAddress().toString();
            String helo = messageContext.getHelo();
            if (messageContext.getTlsPeerCertificates() != null ){
              certificates = (X509Certificate[]) messageContext.getTlsPeerCertificates().clone();
            }
            if (!failed) {
                SmtpEvent message = new SmtpEvent(remoteIP, helo, from, recipient, certificates, messageBody);
                try {
                    messages.put(message);
                } catch (InterruptedException e) {
                    // Perhaps this should be logged?
                }
            }
        }
    }
}
