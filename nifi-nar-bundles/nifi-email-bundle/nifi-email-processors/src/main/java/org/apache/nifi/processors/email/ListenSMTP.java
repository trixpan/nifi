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

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.nifi.annotation.lifecycle.OnStopped;
import org.apache.nifi.flowfile.attributes.CoreAttributes;
import org.apache.nifi.logging.ComponentLog;
import org.apache.nifi.processor.DataUnit;
import org.apache.nifi.processors.email.smtp.event.SmtpEvent;
import org.apache.nifi.processors.email.smtp.handler.SMTPMessageHandlerFactory;
import org.subethamail.smtp.server.SMTPServer;

import org.apache.commons.lang3.StringUtils;
import org.apache.nifi.annotation.lifecycle.OnScheduled;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.processor.AbstractProcessor;
import org.apache.nifi.processor.ProcessorInitializationContext;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.util.StandardValidators;

import org.apache.nifi.annotation.behavior.InputRequirement;
import org.apache.nifi.annotation.behavior.WritesAttribute;
import org.apache.nifi.annotation.behavior.WritesAttributes;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.components.ValidationContext;
import org.apache.nifi.components.ValidationResult;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.io.OutputStreamCallback;
import org.apache.nifi.ssl.SSLContextService;

@Tags({"listen", "email", "smtp"})
@InputRequirement(InputRequirement.Requirement.INPUT_FORBIDDEN)
@CapabilityDescription("This processor implements a lightweight SMTP server to an arbitrary port, " +
        "allowing nifi to lister for incoming email. " +
        "" +
        "Note this server does not perform any email validation. If direct exposure to the internet is sought," +
        "it may be a better idea to use the combination of NiFi and an industrial scale MTA (e.g. Postfix)")
@WritesAttributes({
        @WritesAttribute(attribute = "mime.type", description = "The value used during HELO"),
        @WritesAttribute(attribute = "smtp.helo", description = "The value used during HELO"),
        @WritesAttribute(attribute = "smtp.certificates.*.serial", description = "The serial numbers for each of the " +
                "certificates used by an TLS peer"),
        @WritesAttribute(attribute = "smtp.certificates.*.principal", description = "The principal for each of the " +
                "certificates used by an TLS peer"),
        @WritesAttribute(attribute = "smtp.from", description = "The value used during MAIL FROM (i.e. envelope)"),
        @WritesAttribute(attribute = "smtp.to", description = "The value used during RCPT TO (i.e. envelope)")})

public class ListenSMTP extends AbstractProcessor {
    public static final String SMTP_HELO = "smtp.helo";
    public static final String SMTP_FROM = "smtp.from";
    public static final String SMTP_TO = "smtp.to";
    public static final String MIME_TYPE = "message/rfc822";


    protected static final PropertyDescriptor SMTP_PORT = new PropertyDescriptor.Builder()
            .name("SMTP_PORT")
            .displayName("Listening Port")
            .description("The TCP port the ListenSMTP processor will bind to." +
                    "NOTE that on Unix derivative operating  systems this port must " +
                    "be higher than 1024 unless NiFi is running as with root user permissions.")
            .required(true)
            .expressionLanguageSupported(false)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    protected static final PropertyDescriptor SMTP_HOSTNAME = new PropertyDescriptor.Builder()
            .name("SMTP_HOSTNAME")
            .displayName("SMTP hostname")
            .description("The hostname to be embedded into the banner displayed when an " +
                    "SMTP client connects to the processor TCP port .")
            .required(true)
            .expressionLanguageSupported(false)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    protected static final PropertyDescriptor SMTP_MAXIMUM_CONNECTIONS = new PropertyDescriptor.Builder()
            .name("SMTP_MAXIMUM_CONNECTIONS")
            .displayName("Maximum number of SMTP connection")
            .description("The maximum number of simultaneous SMTP connections.")
            .required(true)
            .expressionLanguageSupported(false)
            .addValidator(StandardValidators.INTEGER_VALIDATOR)
            .build();

    protected static final PropertyDescriptor SMTP_TIMEOUT = new PropertyDescriptor.Builder()
            .name("SMTP_TIMEOUT")
            .displayName("SMTP connection timeout")
            .description("The maximum time to wait for an action of SMTP client.")
            .required(true)
            .expressionLanguageSupported(false)
            .addValidator(StandardValidators.INTEGER_VALIDATOR)
            .build();

    protected static final PropertyDescriptor SMTP_MAXIMUM_MSG_SIZE = new PropertyDescriptor.Builder()
            .name("SMTP_MAXIMUM_MSG_SIZE")
            .displayName("SMTP Maximum Message Size")
            .description("The maximum number of bytes the server will accept.")
            .required(true)
            .defaultValue("20MB")
            .expressionLanguageSupported(false)
            .addValidator(StandardValidators.DATA_SIZE_VALIDATOR)
            .build();

    public static final PropertyDescriptor SSL_CONTEXT_SERVICE = new PropertyDescriptor.Builder()
            .name("SSL_CONTEXT_SERVICE")
            .displayName("SSL Context Service")
            .description("The Controller Service to use in order to obtain an SSL Context. If this property is set, " +
                    "messages will be received over a secure connection.")
            .required(false)
            .identifiesControllerService(SSLContextService.class)
            .build();

    public static final PropertyDescriptor CLIENT_AUTH = new PropertyDescriptor.Builder()
            .name("CLIENT_AUTH")
            .displayName("Client Auth")
            .description("The client authentication policy to use for the SSL Context. Only used if an SSL Context Service is provided.")
            .required(false)
            .allowableValues(SSLContextService.ClientAuth.NONE.toString(), SSLContextService.ClientAuth.REQUIRED.toString())
            .build();

    @Override
    protected Collection<ValidationResult> customValidate(final ValidationContext validationContext) {
        final List<ValidationResult> results = new ArrayList<>();

        final String clientAuth = validationContext.getProperty(CLIENT_AUTH).getValue();
        final SSLContextService sslContextService = validationContext.getProperty(SSL_CONTEXT_SERVICE).asControllerService(SSLContextService.class);

        if (sslContextService != null && StringUtils.isBlank(clientAuth)) {
            results.add(new ValidationResult.Builder()
                    .explanation("Client Auth must be provided when using TLS/SSL")
                    .valid(false).subject("Client Auth").build());
        }

        return results;

    }


    public static final Relationship REL_SUCCESS = new Relationship.Builder()
            .name("success")
            .description("Extraction was successful")
            .build();

    private Set<Relationship> relationships;
    private List<PropertyDescriptor> propertyDescriptors;
    private volatile LinkedBlockingQueue<SmtpEvent> messages;

    private volatile SMTPServer server;
    private AtomicBoolean initialized = new AtomicBoolean(false);

    @Override
    public Set<Relationship> getRelationships() {
        return relationships;
    }

    @Override
    protected List<PropertyDescriptor> getSupportedPropertyDescriptors() {
        return propertyDescriptors;
    }

    @Override
    protected void init(final ProcessorInitializationContext context) {
        final Set<Relationship> relationships = new HashSet<>();
        relationships.add(REL_SUCCESS);
        this.relationships = Collections.unmodifiableSet(relationships);

        final List<PropertyDescriptor> props = new ArrayList<>();
        props.add(SMTP_PORT);
        props.add(SMTP_HOSTNAME);
        props.add(SMTP_MAXIMUM_CONNECTIONS);
        props.add(SMTP_TIMEOUT);
        props.add(SMTP_MAXIMUM_MSG_SIZE);
        props.add(SSL_CONTEXT_SERVICE);
        props.add(CLIENT_AUTH);
        this.propertyDescriptors = Collections.unmodifiableList(props);

    }

    final ComponentLog logger = getLogger();

    // Upon Schedule, reset the initialized state to false
    @OnScheduled
    public void onScheduled() {
        initialized.set(false);
    }

    private synchronized void initializeSMTPServer(final ProcessContext context) throws Exception {
        if (initialized.get()) {
            return;
        }

        messages = new LinkedBlockingQueue<>(1024);
        String clientAuth = null;

        new SMTPMessageHandlerFactory(messages);

        // If an SSLContextService was provided then create an SSLContext to pass down to the server
        SSLContext sslContext = null;
        final SSLContextService sslContextService = context.getProperty(SSL_CONTEXT_SERVICE).asControllerService(SSLContextService.class);
        if (sslContextService != null) {
            clientAuth = context.getProperty(CLIENT_AUTH).getValue();
            sslContext = sslContextService.createSSLContext(SSLContextService.ClientAuth.valueOf(clientAuth));
        }

        final SSLContext finalSslContext = sslContext;
        final String finalClientAuth = clientAuth;

        SMTPMessageHandlerFactory smtpMessageHandlerFactory = new SMTPMessageHandlerFactory(messages);
        final SMTPServer server = new SMTPServer(smtpMessageHandlerFactory) {

            @Override
            public SSLSocket createSSLSocket(Socket socket) throws IOException {
                InetSocketAddress remoteAddress = (InetSocketAddress) socket.getRemoteSocketAddress();

                SSLSocketFactory socketFactory = finalSslContext.getSocketFactory();

                SSLSocket s = (SSLSocket) (socketFactory.createSocket(socket, remoteAddress.getHostName(), socket.getPort(), true));

                s.setUseClientMode(false);


                // For some reason the createSSLContext above is not enough to enforce
                // client side auth
                // If client auth is required...
                if (finalClientAuth == "REQUIRED") {
                    s.setNeedClientAuth(true);
                }


                return s;
            }
        };


        // Set some parameters to our server
        server.setSoftwareName("");


        // Set the Server options based on properties
        server.setPort(context.getProperty(SMTP_PORT).asInteger());
        server.setHostName(context.getProperty(SMTP_HOSTNAME).getValue());
        server.setMaxMessageSize(context.getProperty(SMTP_MAXIMUM_MSG_SIZE).asDataSize(DataUnit.B).intValue());
        server.setMaxConnections(context.getProperty(SMTP_MAXIMUM_CONNECTIONS).asInteger());
        server.setConnectionTimeout(context.getProperty(SMTP_TIMEOUT).asInteger());

        // Check if TLS should be enabled
        if (sslContextService != null) {
            server.setEnableTLS(true);
        } else {
            server.setHideTLS(true);
        }

        // Set TLS to required in case CLIENT_AUTH = required
        if (context.getProperty(CLIENT_AUTH).getValue() == "REQUIRED") {
            server.setRequireTLS(true);
        }

        this.server = server;
        server.start();

        getLogger().info("Server started and listening on port " + server.getPort());

        initialized.set(true);
    }

    @OnStopped
    public void shutdown() throws Exception {
        if (server != null) {
            getLogger().debug("Shutting down server");
            server.stop();
            getLogger().info("Shut down {}", new Object[]{server});
        }
    }

    @Override
    public void onTrigger(ProcessContext context, ProcessSession session) throws ProcessException {
        try {
            initializeSMTPServer(context);
        } catch (Exception e) {
            context.yield();
            throw new ProcessException("Failed to initialize the SMTP server", e);
        }

        if (messages.isEmpty()) {
            return;
        }

        FlowFile flowfile = session.create();
        SmtpEvent message;

        try {
            message = messages.take();

            if (message.getBodyData() != null) {
                byte[] messageData = message.getBodyData();
                flowfile = session.write(flowfile, new OutputStreamCallback() {

                    // Write the messageData to flowfile content
                    @Override
                    public void process(OutputStream out) throws IOException {
                        out.write(messageData);
                    }
                });
            }

            HashMap<String, String> attributes = new HashMap<>();
            // Gather message attributes
            attributes.put(SMTP_HELO, message.getHelo());
            attributes.put(SMTP_FROM, message.getFrom());
            attributes.put(SMTP_TO, message.getTo());

            List<Map> details = message.getCertifcateDetails();
            int c = 0;

            // Add a selection of each X509 certificates to the already gathered attributes

            for (Map<String, String> detail : details) {
                attributes.put("smtp.certificate." + "c".toString() + "serial", detail.getOrDefault("SerialNumber", null));
                attributes.put("smtp.certificate." + "c".toString() + "subjectName", detail.getOrDefault("SubjectName", null));
                c++;
            }

            // Set Mime-Type
            attributes.put(CoreAttributes.MIME_TYPE.key(), MIME_TYPE);

            // Add the attributes. to flowfile
            flowfile = session.putAllAttributes(flowfile, attributes);
            session.getProvenanceReporter().receive(flowfile, "smtp://" + message.getRemoteIP() + "/");
            session.transfer(flowfile, REL_SUCCESS);
            getLogger().info("Transferring {} to success", new Object[]{flowfile});


        } catch (InterruptedException e) {
            getLogger().error("Error processing SMTP message", e);
            session.remove(flowfile);
        }


    }

    // Same old... same old... used for testing to access the random port that was selected
    protected int getPort() {
        return server == null ? 0 : server.getPort();
    }


}
