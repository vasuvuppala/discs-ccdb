package org.openepics.discs.conf.ent;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.persistence.Basic;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Index;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * An Installation Record contains information connects device instances with the installation slots
 *
 * @author vuppala
 */
// when searching by device only, the composite index (device, uninstall_date) can be used as well
@Entity
@Table(name = "installation_record", indexes = { @Index(columnList = "slot"),
        @Index(columnList = "device, uninstall_date") })
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "InstallationRecord.activeRecordForSlot", query = "SELECT i FROM InstallationRecord i "
            + "WHERE i.slot = :slot AND i.uninstallDate IS NULL "),
    @NamedQuery(name = "InstallationRecord.activeRecordForDevice", query = "SELECT i FROM InstallationRecord i "
            + "WHERE i.device = :device AND i.uninstallDate IS NULL ")
})
public class InstallationRecord extends ConfigurationEntity {
    @Basic(optional = false)
    @NotNull
    @Size(min = 1, max = 64)
    @Column(name = "record_number")
    private String recordNumber;

    @Basic(optional = false)
    @NotNull
    @Column(name = "install_date")
    @Temporal(TemporalType.DATE)
    private Date installDate;

    @Column(name = "uninstall_date")
    @Temporal(TemporalType.DATE)
    private Date uninstallDate;

    @Column(name = "notes", columnDefinition="TEXT")
    private String notes;

    @JoinColumn(name = "slot")
    @ManyToOne(optional = false)
    private Slot slot;

    @JoinColumn(name = "device")
    @ManyToOne(optional = false)
    private Device device;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "installationRecord")
    private List<InstallationArtifact> installationArtifactList = new ArrayList<>();

    protected InstallationRecord() {
    }

    public InstallationRecord(String recordNumber, Date installDate) {
        this.recordNumber = recordNumber;
        this.installDate = new Date(installDate.getTime());
    }

    public String getRecordNumber() {
        return recordNumber;
    }
    public void setRecordNumber(String recordNumber) {
        this.recordNumber = recordNumber;
    }

    public Date getInstallDate() {
        return installDate!=null ? new Date(installDate.getTime()) : null;
    }
    public void setInstallDate(Date installDate) {
        this.installDate = new Date(installDate.getTime());
    }

    public Date getUninstallDate() {
        return uninstallDate!=null ? new Date(uninstallDate.getTime()) : null;
    }
    public void setUninstallDate(Date uninstallDate) {
        this.uninstallDate = new Date(uninstallDate.getTime());
    }

    public String getNotes() {
        return notes;
    }
    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Slot getSlot() {
        return slot;
    }
    public void setSlot(Slot slot) {
        this.slot = slot;
    }

    public Device getDevice() {
        return device;
    }
    public void setDevice(Device device) {
        this.device = device;
    }

    @XmlTransient
    @JsonIgnore
    public List<InstallationArtifact> getInstallationArtifactList() {
        return installationArtifactList;
    }

    @Override
    public String toString() {
        return "InstallationRecord[ installationRecordId=" + id + " ]";
    }
}
