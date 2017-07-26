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

import org.apache.nifi.annotation.behavior.WritesAttribute;
import org.apache.nifi.annotation.behavior.WritesAttributes;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.processor.AbstractProcessor;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.util.StandardValidators;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@WritesAttributes({
        @WritesAttribute(attribute = "taxii.return_code", description = "")})
abstract class AbstractTaxiiProcessor extends AbstractProcessor {

    public static final PropertyDescriptor SERVER_URI = new PropertyDescriptor.Builder()
            .name("SERVER_URI")
            .displayName("TAXII Server URL")
            .description("The URL to the TAXII server to be contacted")
            .expressionLanguageSupported(true)
            .addValidator(StandardValidators.URI_VALIDATOR)
            .required(true)
            .build();

    public static final PropertyDescriptor SERVER_USERNAME = new PropertyDescriptor.Builder()
            .name("SERVER_USERNAME")
            .displayName("Username")
            .description("Username to use for authentications against the TAXII server")
            .expressionLanguageSupported(true)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    public static final PropertyDescriptor SERVER_PASSWORD = new PropertyDescriptor.Builder()
            .name("SERVER_PASSWORD")
            .displayName("Password")
            .description("Password to use for authentications against the TAXII server")
            .expressionLanguageSupported(true)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    public static final PropertyDescriptor PROXY_HOST = new PropertyDescriptor.Builder()
            .name("PROXY_HOST")
            .displayName("Proxy Host")
            .description("The fully qualified hostname or IP address of the proxy server")
            .expressionLanguageSupported(true)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    public static final PropertyDescriptor PROXY_PORT = new PropertyDescriptor.Builder()
            .name("PROXY_PORT")
            .displayName("Proxy Port")
            .description("The port of the proxy server")
            .expressionLanguageSupported(true)
            .addValidator(StandardValidators.PORT_VALIDATOR)
            .build();
    public static final PropertyDescriptor PROXY_USERNAME = new PropertyDescriptor.Builder()
            .name("PROXY_USERNAME")
            .displayName("Proxy Username")
            .description("Proxy Username")
            .expressionLanguageSupported(true)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .required(false)
            .build();
    public static final PropertyDescriptor PROXY_PASSWORD = new PropertyDescriptor.Builder()
            .name("PROXY_PASSWORD")
            .displayName("Proxy Password")
            .description("Proxy Password")
            .expressionLanguageSupported(true)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .required(false)
            .sensitive(true)
            .build();


    public static final Relationship REL_SUCCESS = new Relationship.Builder()
            .name("success")
            .description("All FlowFiles that are received are routed to success")
            .build();

    public static final Relationship REL_FAILURE = new Relationship.Builder()
            .name("failure")
            .description("All FlowFiles failures are routed to this relationship")
            .build();

    public static final List<PropertyDescriptor> properties = Collections.unmodifiableList(
            Arrays.asList(SERVER_URI, SERVER_USERNAME, SERVER_PASSWORD, PROXY_HOST, PROXY_PORT, PROXY_USERNAME, PROXY_PASSWORD));

    public static final Set<Relationship> relationships = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(REL_SUCCESS, REL_FAILURE)));



}
