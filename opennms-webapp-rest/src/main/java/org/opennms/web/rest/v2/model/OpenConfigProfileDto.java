/*
 * Licensed to The OpenNMS Group, Inc (TOG) under one or more
 * contributor license agreements.  See the LICENSE.md file
 * distributed with this work for additional information
 * regarding copyright ownership.
 *
 * TOG licenses this file to You under the GNU Affero General
 * Public License Version 3 (the "License") or (at your option)
 * any later version.  You may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at:
 *
 *      https://www.gnu.org/licenses/agpl-3.0.txt
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the
 * License.
 */
package org.opennms.web.rest.v2.model;

/**
 * REST payload for an OpenConfig subscription profile (NMS-19857).
 */
public class OpenConfigProfileDto {

    private Integer id;
    private String name;
    private String description;
    private String transport;
    private String encoding;
    private Long sampleInterval;
    private String paths;
    private String origin;
    private Integer rrdStep;
    private String rrdRras;
    private String modelSet;
    private Boolean enabled;
    private String uploadedBy;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getTransport() {
        return transport;
    }

    public void setTransport(String transport) {
        this.transport = transport;
    }

    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public Long getSampleInterval() {
        return sampleInterval;
    }

    public void setSampleInterval(Long sampleInterval) {
        this.sampleInterval = sampleInterval;
    }

    public String getPaths() {
        return paths;
    }

    public void setPaths(String paths) {
        this.paths = paths;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public Integer getRrdStep() {
        return rrdStep;
    }

    public void setRrdStep(Integer rrdStep) {
        this.rrdStep = rrdStep;
    }

    public String getRrdRras() {
        return rrdRras;
    }

    public void setRrdRras(String rrdRras) {
        this.rrdRras = rrdRras;
    }

    public String getModelSet() {
        return modelSet;
    }

    public void setModelSet(String modelSet) {
        this.modelSet = modelSet;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(String uploadedBy) {
        this.uploadedBy = uploadedBy;
    }
}
