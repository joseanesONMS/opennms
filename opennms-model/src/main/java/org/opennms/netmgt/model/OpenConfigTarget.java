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

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.UniqueConstraint;
import java.io.Serializable;
import java.util.Date;

/**
 * Binds an {@link OpenConfigSubscriptionProfile} to a set of devices, either by an OpenNMS filter
 * rule ({@code matchType=FILTER}) or by an explicit node-id list ({@code matchType=NODES}), and
 * carries the per-device transport details (port, credential/TLS references). Credentials are not
 * stored here: {@code credentialRef}/{@code tlsRef} are aliases into the Secure Credentials Vault.
 * (NMS-19857)
 */
@Entity
@Table(
        name = "openconfig_targets",
        uniqueConstraints = @UniqueConstraint(columnNames = {"profile_id", "name"})
)
public class OpenConfigTarget implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "openconfig_targets_seq")
    @SequenceGenerator(
            name = "openconfig_targets_seq",
            sequenceName = "openconfig_targets_id_seq",
            allocationSize = 1
    )
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", nullable = false)
    private OpenConfigSubscriptionProfile profile;

    @Column(nullable = false, length = 256)
    private String name;

    /** {@code FILTER} (use {@link #filterRule}) or {@code NODES} (use {@link #nodeIds}). */
    @Column(name = "match_type", nullable = false, length = 32)
    private String matchType = "FILTER";

    @Column(name = "filter_rule", columnDefinition = "text")
    private String filterRule;

    /** Comma-separated node ids, used when {@code matchType=NODES}. */
    @Column(name = "node_ids", columnDefinition = "text")
    private String nodeIds;

    @Column
    private Integer port;

    /** Secure Credentials Vault alias for username/password; never the secret itself. */
    @Column(name = "credential_ref", length = 256)
    private String credentialRef;

    /** Secure Credentials Vault alias for TLS material; never the secret itself. */
    @Column(name = "tls_ref", length = 256)
    private String tlsRef;

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

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public OpenConfigSubscriptionProfile getProfile() {
        return profile;
    }

    public void setProfile(OpenConfigSubscriptionProfile profile) {
        this.profile = profile;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMatchType() {
        return matchType;
    }

    public void setMatchType(String matchType) {
        this.matchType = matchType;
    }

    public String getFilterRule() {
        return filterRule;
    }

    public void setFilterRule(String filterRule) {
        this.filterRule = filterRule;
    }

    public String getNodeIds() {
        return nodeIds;
    }

    public void setNodeIds(String nodeIds) {
        this.nodeIds = nodeIds;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getCredentialRef() {
        return credentialRef;
    }

    public void setCredentialRef(String credentialRef) {
        this.credentialRef = credentialRef;
    }

    public String getTlsRef() {
        return tlsRef;
    }

    public void setTlsRef(String tlsRef) {
        this.tlsRef = tlsRef;
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
}
