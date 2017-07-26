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

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.ProxyAuthenticationStrategy;
import org.apache.nifi.annotation.behavior.EventDriven;
import org.apache.nifi.annotation.behavior.InputRequirement;
import org.apache.nifi.annotation.behavior.SideEffectFree;
import org.apache.nifi.annotation.behavior.SupportsBatching;
import org.apache.nifi.annotation.behavior.WritesAttribute;
import org.apache.nifi.annotation.behavior.WritesAttributes;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.util.StringUtils;
import org.mitre.taxii.client.HttpClient;
import org.mitre.taxii.messages.xml11.CollectionInformationRequest;
import org.mitre.taxii.messages.xml11.CollectionInformationResponse;
import org.mitre.taxii.messages.xml11.CollectionRecordType;
import org.mitre.taxii.messages.xml11.MessageHelper;
import org.mitre.taxii.messages.xml11.ObjectFactory;
import org.mitre.taxii.messages.xml11.StatusMessage;

import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@EventDriven
@SideEffectFree
@SupportsBatching
@InputRequirement(InputRequirement.Requirement.INPUT_REQUIRED)
@Tags({"ioc", "taxii", "cyber-security"})
@CapabilityDescription("This processor connects to a TAXII end-point and lists the TAXII collections made available by the service")
@WritesAttributes({
        @WritesAttribute(attribute = "taxii.collection_name", description = ""),
        @WritesAttribute(attribute = "taxii.collection_type", description = ""),
        @WritesAttribute(attribute = "taxii.collection_volume", description = "")})
public class FetchTaxii extends AbstractTaxiiProcessor {

    @Override
    protected List<PropertyDescriptor> getSupportedPropertyDescriptors() {
        return properties;
    }

    @Override
    public Set<Relationship> getRelationships() {
        return relationships;
    }


    @Override
    public void onTrigger(ProcessContext context, ProcessSession session) throws ProcessException {

        FlowFile flowFile = session.get();

        if (flowFile == null) {
            return;
        }

        String proxyHost = context.getProperty(PROXY_HOST).evaluateAttributeExpressions(flowFile).getValue();
        Integer proxyPort = context.getProperty(PROXY_PORT).evaluateAttributeExpressions(flowFile).asInteger();
        String proxyUsername = context.getProperty(PROXY_USERNAME).evaluateAttributeExpressions(flowFile).getValue();
        String proxyPassword = context.getProperty(PROXY_PASSWORD).evaluateAttributeExpressions(flowFile).getValue();


        // Create a HttpClientBuilder to be fed to the TAXII client
        HttpClientBuilder httpClientBuilder = HttpClientBuilder.create();
        final CredentialsProvider proxyCredentialsProvider = new BasicCredentialsProvider();

        if (!StringUtils.isBlank(proxyHost) && proxyPort > 0) {
            if (!StringUtils.isBlank(proxyUsername)) {
                proxyCredentialsProvider.setCredentials(
                        new AuthScope(proxyHost, proxyPort),
                        new UsernamePasswordCredentials(proxyUsername, proxyPassword)
                );
            }
            httpClientBuilder.setProxy(new HttpHost(proxyHost, proxyPort))
                    .setProxyAuthenticationStrategy(new ProxyAuthenticationStrategy())
                    .setDefaultCredentialsProvider(proxyCredentialsProvider);
        }

        HttpClient taxiiClient = new HttpClient();

        // Finally feed with the configured HttpClient
        taxiiClient.setHttpclient(
                httpClientBuilder
                        .setUserAgent(this.getClass().getName())
                        .build()
        );

        ObjectFactory factory;

        // Create a version specific Object factory
        factory = new ObjectFactory();


        List<CollectionRecordType> collectionsAvailable = new ArrayList<>();

        try {
            // Creates an URI from the provided TAXII server property
            // If the URI is invalid, this will be captured by URISyntaxException
            URI targetTaxiiEndpoint = new URI(context.getProperty(SERVER_URI).evaluateAttributeExpressions(flowFile).getValue());

            // Create a TAXII request payload
            CollectionInformationRequest dr = factory.createCollectionInformationRequest().withMessageId(MessageHelper.generateMessageId());

            // then call the TAXII server and push the payload, returning an Object or throwing JAXBException or IOException
            final Object responseObject = taxiiClient.callTaxiiService(targetTaxiiEndpoint, dr);

            // In case of success callTaxiiService will return a valid response
            if (responseObject instanceof CollectionInformationResponse) {
                final CollectionInformationResponse collectionInformationResponse = (CollectionInformationResponse) responseObject;
                collectionsAvailable = collectionInformationResponse.getCollections();

                // iterate over the response, use the values to populate the flowfile attributes
                for (CollectionRecordType collection : collectionsAvailable) {
                    Map<String, String> attributes = new HashMap<>();

                    final String collectionName = collection.getCollectionName();
                    if (!StringUtils.isBlank(collectionName)) {
                        attributes.put("taxii.collection_name", collectionName);
                    }

                    final String collectionType = collection.getCollectionType().value();
                    if (!StringUtils.isBlank(collectionType)) {
                        attributes.put("taxii.collection_type", collectionType);
                    }


                    final String collectionVolume = String.valueOf(collection.getCollectionVolume());
                    if (!StringUtils.isBlank(collectionVolume)) {
                        attributes.put("taxii.collection_volume", collectionVolume);
                    }

                    FlowFile downStreamFlowFile = session.create(flowFile);

                    // Add attributes to downStreamFlowfile
                    downStreamFlowFile = session.putAllAttributes(downStreamFlowFile, attributes);

                    session.transfer(downStreamFlowFile, REL_SUCCESS);
                    session.getProvenanceReporter().create(downStreamFlowFile);
                }

                // The original flowfile has been processed and should be ready to be removed
                session.remove(flowFile);

            } else if (responseObject instanceof StatusMessage) {
                // otherwise... it seems callTaxiiService will return StatusMessages in case of errors
                final StatusMessage statusMessage = (StatusMessage) responseObject;

                final String statusType = statusMessage.getStatusType();

                // Server returned error so add response to attribute before routing flow to failure
                session.putAttribute(flowFile, "taxii.status_type", statusType);

                // FAILURE is raised when API endpoint doesn't support the request type
                switch(statusType) {
                    case "FAILURE":
                    case "BAD_MESSAGE":
                    case "NOT_FOUND":
                        session.transfer(flowFile, REL_FAILURE);
                        getLogger().error("Server returned an error to request. Routing {} to failure.", new Object[]{flowFile});
                        session.getProvenanceReporter().route(flowFile, REL_FAILURE);
                        break;
                    case "UNAUTHORIZED":
                        session.transfer(flowFile, REL_FAILURE);
                        getLogger().error("Server rejected credentials. Routing {} to failure.", new Object[]{flowFile});
                        session.getProvenanceReporter().route(flowFile, REL_FAILURE);
                        break;
                    default:
                        session.transfer(flowFile, REL_FAILURE);
                        getLogger().error("Unexpected Status Type '" + statusType + "' returned by TAXII server. Consider raising a bug with the NiFi project");
                        session.getProvenanceReporter().route(flowFile, REL_FAILURE);
                        break;
                }
            }
        } catch (JAXBException | IOException e) {
            session.transfer(flowFile, REL_FAILURE);
            getLogger().error("Hit {} when processing {}. Routing to failure.", new Object[]{e.getMessage(), flowFile});
            session.getProvenanceReporter().route(flowFile, REL_FAILURE);
        } catch (URISyntaxException e) {
            session.transfer(flowFile, REL_FAILURE);
            getLogger().error("The TAXII URI provided is not valid URI. Routing to failure");
            session.getProvenanceReporter().route(flowFile, REL_FAILURE);
        }

        // and commit
        session.commit();
    }
}
