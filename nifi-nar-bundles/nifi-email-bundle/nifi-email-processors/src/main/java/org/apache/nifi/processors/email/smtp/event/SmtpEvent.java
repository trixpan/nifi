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

package org.apache.nifi.processors.email.smtp.event;


import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A Smtp event which adds the transaction number and command to the StandardEvent.
 */

public class SmtpEvent{

    private final String remoteIP;
    private final String helo;
    private final String from;
    private final String to;
    private final byte[] bodyData;
    private X509Certificate[] certificates;
    private List<Map<String, String>> certificatesDetails;

    public SmtpEvent(final String remoteIP, final String helo, final String from, final String to, final X509Certificate[] certificates, final byte[] bodyData) {
        this.remoteIP = remoteIP;
        this.helo = helo;
        this.from = from;
        this.to = to;
        this.bodyData = bodyData;
        this.certificates = certificates;

        this.certificatesDetails = new ArrayList<>();

        for (int c = 0; c < certificates.length; c++) {
            X509Certificate cert = certificates[c];
            if (cert.getSerialNumber() != null && cert.getSubjectDN() != null) {
                Map<String, String> certificate = new HashMap<>();

                String certSerialNumber = cert.getSerialNumber().toString();
                String certSubjectDN = cert.getSubjectDN().getName();


                certificate.put("SerialNumber", certSerialNumber);
                certificate.put("SubjectName", certSubjectDN);

                certificatesDetails.add(certificate);

            }
        }
    }

    public List getCertifcateDetails() {
        return certificatesDetails;
    }

    public String getHelo() {
        return helo;
    }

    public byte [] getBodyData() {
        return bodyData;
    }

    public String getFrom() {
        return from;
    }

    public String getTo() {
        return to;
    }

    public String getRemoteIP() {
        return remoteIP;
    }
}

