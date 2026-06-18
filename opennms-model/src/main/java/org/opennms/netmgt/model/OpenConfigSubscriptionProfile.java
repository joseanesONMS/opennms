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
package org.opennms.netmgt.model;


import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.UniqueConstraint;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * An OpenConfig streaming-telemetry subscription profile: the reusable "what and how" of a
 * subscription (transport, encoding, sample interval, paths, RRD storage) that can be bound to many
 * devices via {@link OpenConfigTarget}s. Mirrors the SNMP data-collection source/profile model
 * introduced in 36.0.0. (NMS-19857)
 */
@Entity
@Table(name = "openconfig_subscription_profiles", uniqueConstraints = @UniqueConstraint(columnNames = {"name"}))
public class OpenConfigSubscriptionProfile implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "openconfig_subscription_profiles_seq")
    @SequenceGenerator(
            name = "openconfig_subscription_profiles_seq",
            sequenceName = "openconfig_subscription_profiles_id_seq",
            allocationSize = 1
    )
    private Integer id;

    @Column(nullable = false, length = 256, unique = true)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    /** Stored as the name of an {@code org.opennms.features.openconfig.api.OpenConfigTransport}. */
    @Column(nullable = false, length = 64)
    private String transport = "GNMI_DIALIN";

    @Column(nullable = false, length = 64)
    private String encoding = "PROTO";

    /** Sample interval; nanoseconds for gNMI, milliseconds for JTI (interpreted by the transport). */
    @Column(name = "sample_interval")
    private Long sampleInterval;

    @Column(columnDefinition = "text")
    private String paths;

    @Column(length = 128)
    private String origin = "openconfig";

    @Column(name = "rrd_step", nullable = false)
    private Integer rrdStep = 300;

    @Column(name = "rrd_rras", columnDefinition = "text")
    private String rrdRras;

    /** Name of the YANG model set that decodes this subscription (null = legacy Groovy decoding). */
    @Column(name = "model_set", length = 256)
    private String modelSet;

    @Column(nullable = false)
    private Boolean enabled = Boolean.TRUE;

    @Column(name = "created_time", nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdTime;

    @Column(name = "last_modified")
    @Temporal(TemporalType.TIMESTAMP)
    private Date lastModified;

    @Column(name = "uploaded_by", length = 256)
    private String uploadedBy;

    @OneToMany(mappedBy = "profile", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OpenConfigTarget> targets;

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

    public Date getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(Date createdTime) {
        this.createdTime = createdTime;
    }

    public Date getLastModified() {
        return lastModified;
    }

    public void setLastModified(Date lastModified) {
        this.lastModified = lastModified;
    }

    public String getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(String uploadedBy) {
        this.uploadedBy = uploadedBy;
    }

    public List<OpenConfigTarget> getTargets() {
        return targets;
    }

    public void setTargets(List<OpenConfigTarget> targets) {
        this.targets = targets;
    }
}
