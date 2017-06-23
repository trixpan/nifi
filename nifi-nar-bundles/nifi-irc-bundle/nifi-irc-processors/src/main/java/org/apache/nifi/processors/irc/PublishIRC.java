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
package org.apache.nifi.processors.irc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import org.apache.nifi.annotation.behavior.InputRequirement;
import org.apache.nifi.annotation.behavior.TriggerSerially;
import org.apache.nifi.annotation.behavior.WritesAttribute;
import org.apache.nifi.annotation.behavior.WritesAttributes;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.annotation.lifecycle.OnStopped;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.ProcessSessionFactory;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.io.InputStreamCallback;
import org.apache.nifi.stream.io.StreamUtils;
import org.apache.nifi.util.StopWatch;

@Tags({"publish", "irc"})
@TriggerSerially
@InputRequirement(InputRequirement.Requirement.INPUT_REQUIRED)

@CapabilityDescription("This processor implements a IRC client allowing nifi to publish flowfile contents into a " +
        "predefined IRC channel. " + "\n" +
        "IMPORTANT NOTE: Due to the nature of the IRC protocol, no delivery guarantees are offered. USE WITH CARE")

@WritesAttributes({
        @WritesAttribute(attribute = "irc.sender", description = "The IRC nickname source user of the message"),
        @WritesAttribute(attribute = "irc.channel", description = "The channel from where the message was received "),
        @WritesAttribute(attribute = "irc.server", description = "The values IRC channel where the message was received from")})
public class PublishIRC extends AbstractIRCProcessor {

    private volatile String channel = null;
    @OnStopped
    public void onUnscheduled(ProcessContext context) {
        if(channel != null) {
            ircClientService.leaveChannel(this.getIdentifier(), channel);
            channel = null;
        }
    }

    @Override
    public void onTrigger(ProcessContext context, ProcessSessionFactory sessionFactory) throws ProcessException {
        ProcessSession session = sessionFactory.createSession();

        if (channel == null) {
            // Initialise the handler that will be provided to the setupClient super method
            channel = context.getProperty(IRC_CHANNEL).getValue();
            ircClientService.joinChannel(this.getIdentifier(), channel, null);
        }

        FlowFile flowFile = session.get();
        if (flowFile == null) {
            return;
        }

        final StopWatch watch = new StopWatch();
        watch.start();
        
        try {
            ircClientService.sendMessage(channel, readContent(session, flowFile).toString());
    
            session.transfer(flowFile, REL_SUCCESS);
            watch.stop();
            session.getProvenanceReporter().send(flowFile, "irc://"
                    .concat(ircClientService.getServer())
                    .concat("/")
                    // Device if append channel to URI or not
                    .concat(channel.concat("/")),
                    watch.getDuration(TimeUnit.MILLISECONDS)
                    );
        } catch (Exception e) {
                // The client seems to be waiting for join command to complete, rollback for now
            session.rollback(false);
            context.yield();
        }
        session.commit();
    }

    /**
     * Helper method to read the FlowFile content stream into a ByteArrayOutputStream object.
     *
     * @param session
     *            - the current process session.
     * @param flowFile
     *            - the FlowFile to read the content from.
     *
     * @return ByteArrayOutputStream object containing the FlowFile content.
     */
    protected ByteArrayOutputStream readContent(final ProcessSession session, final FlowFile flowFile) {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream((int) flowFile.getSize() + 1);
        session.read(flowFile, new InputStreamCallback() {
            @Override
            public void process(final InputStream in) throws IOException {
                StreamUtils.copy(in, baos);
            }
        });

        return baos;
    }

}
