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
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.UniqueConstraint;
import java.io.Serializable;
import java.util.Date;

/**
 * A single YANG module's source, keyed by name + revision (NMS-19857, Phase 2). The raw {@code .yang}
 * text is stored in {@link #content}; modules are grouped into import-closure-complete
 * {@link YangModelSet}s that compile into a schema context.
 */
@Entity
@Table(name = "yang_modules", uniqueConstraints = @UniqueConstraint(columnNames = {"name", "revision"}))
public class YangModule implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "yang_modules_seq")
    @SequenceGenerator(
            name = "yang_modules_seq",
            sequenceName = "yang_modules_id_seq",
            allocationSize = 1
    )
    private Integer id;

    @Column(nullable = false, length = 256)
    private String name;

    /** YANG revision date (yyyy-mm-dd), or null for an unrevisioned module. */
    @Column(length = 32)
    private String revision;

    @Column(length = 512)
    private String namespace;

    @Column(name = "openconfig_version", length = 64)
    private String openconfigVersion;

    @Column(columnDefinition = "text", nullable = false)
    private String content;

    @Column(name = "source_filename", length = 512)
    private String sourceFilename;

    @Column(name = "uploaded_by", length = 256)
    private String uploadedBy;

    @Column(name = "created_time", nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdTime;

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

    public String getRevision() {
        return revision;
    }

    public void setRevision(String revision) {
        this.revision = revision;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getOpenconfigVersion() {
        return openconfigVersion;
    }

    public void setOpenconfigVersion(String openconfigVersion) {
        this.openconfigVersion = openconfigVersion;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getSourceFilename() {
        return sourceFilename;
    }

    public void setSourceFilename(String sourceFilename) {
        this.sourceFilename = sourceFilename;
    }

    public String getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(String uploadedBy) {
        this.uploadedBy = uploadedBy;
    }

    public Date getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(Date createdTime) {
        this.createdTime = createdTime;
    }
}
