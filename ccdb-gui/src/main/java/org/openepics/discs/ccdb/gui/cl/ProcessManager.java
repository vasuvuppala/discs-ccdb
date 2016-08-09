/*
 * This software is Copyright by the Board of Trustees of Michigan
 *  State University (c) Copyright 2013, 2014.
 *  
 *  You may use this software under the terms of the GNU public license
 *  (GPL). The terms of this license are described at:
 *    http://www.gnu.org/licenses/gpl.txt
 *  
 *  Contact Information:
 *       Facility for Rare Isotope Beam
 *       Michigan State University
 *       East Lansing, MI 48824-1321
 *        http://frib.msu.edu
 */

package org.openepics.discs.ccdb.gui.cl;

import org.openepics.discs.ccdb.gui.ui.util.InputAction;
import java.io.Serializable;
import java.util.List;
import java.util.logging.Logger;
import javax.annotation.PostConstruct;
import javax.ejb.EJB;
import javax.faces.application.FacesMessage;
import javax.faces.event.ActionEvent;
import javax.inject.Named;
import javax.faces.view.ViewScoped;
import org.openepics.discs.ccdb.core.ejb.ChecklistEJB;
import org.openepics.discs.ccdb.gui.ui.util.UiUtility;
import org.openepics.discs.ccdb.model.cl.Process;
import org.openepics.discs.ccdb.model.cl.Checklist;

import org.primefaces.context.RequestContext;
import org.primefaces.event.SelectEvent;

/**
 * Description: State for Manage Phase View
 *
 * Methods:
 * <p>
 * Init: to initialize the state
 * <p>
 * resetInput: reset all inputs on the view
 * <p>
 * onRowSelect: things to do when an item is selected
 * <p>
 * onAddCommand: things to do before adding an item
 * <p>
 * onEditCommand: things to do before editing an item
 * <p>
 * onDeleteCommand: things to do before deleting an item
 * <p>
 * saveXXXX: save the input or edited item
 * <p>
 * deleteXXXX: delete the selected item
 *
 * @author vuppala
 *
 */

@Named
@ViewScoped
public class ProcessManager implements Serializable {
//    @EJB
//    private AuthEJB authEJB;
    @EJB
    private ChecklistEJB lcEJB;
            
    private static final Logger LOGGER = Logger.getLogger(ProcessManager.class.getName());
//    @Inject
//    UserSession userSession;
      
    private List<Process> entities;    
    private List<Process> filteredEntities;    
    private Process inputEntity;
    private Process selectedEntity;
    private InputAction inputAction;
    private String selectedType;
    
    private List<Checklist> statusTypes;
    
    public ProcessManager() {
    }
    
    @PostConstruct
    public void init() { 
        initialize();
        statusTypes = lcEJB.findAllChecklists();
        resetInput();
    }
    
    /**
     * Initialize data in view
     * 
     * @return 
     */
    public String initialize() {
        String nextView = null;
        Checklist stype = null;
        
        if (selectedType != null) {
            stype = lcEJB.findChecklist(selectedType);
        }
        if (stype == null) {
            entities = lcEJB.findAllProcesses();
        } else {
            entities = lcEJB.findPhases(stype);
        }
        
        return nextView;
    
    }
    
    private void resetInput() {                
        inputAction = InputAction.READ;
    }
    
    public void onRowSelect(SelectEvent event) {
        // inputRole = selectedRole;
        // Utility.showMessage(FacesMessage.SEVERITY_INFO, "Role Selected", "");
    }
    
    public void onAddCommand(ActionEvent event) {
        inputEntity = new Process();
        inputAction = InputAction.CREATE;       
    }
    
    public void onEditCommand(ActionEvent event) {
        inputAction = InputAction.UPDATE;
    }
    
    public void onDeleteCommand(ActionEvent event) {
        inputAction = InputAction.DELETE;
    }
    
    public void saveEntity() {
        try {                      
            if (inputAction == InputAction.CREATE) {
                lcEJB.savePhase(inputEntity);
                entities.add(inputEntity);                
            } else {
                lcEJB.savePhase(selectedEntity);
            }
            resetInput();
            RequestContext.getCurrentInstance().addCallbackParam("success", true);
            UiUtility.showMessage(FacesMessage.SEVERITY_INFO, "Saved", "");
        } catch (Exception e) {
            UiUtility.showMessage(FacesMessage.SEVERITY_ERROR, "Could not save", e.getMessage());
            RequestContext.getCurrentInstance().addCallbackParam("success", false);
            System.out.println(e);
        }
    }
    
    public void deleteEntity() {
        try {
            lcEJB.deletePhase(selectedEntity);
            entities.remove(selectedEntity);  
            RequestContext.getCurrentInstance().addCallbackParam("success", true);
            UiUtility.showMessage(FacesMessage.SEVERITY_INFO, "Deletion successful", "You may have to refresh the page.");
            resetInput();
        } catch (Exception e) {
            UiUtility.showMessage(FacesMessage.SEVERITY_ERROR, "Could not complete deletion", e.getMessage());
            RequestContext.getCurrentInstance().addCallbackParam("success", false);
            System.out.println(e);
        }
    }
    
    //-- Getters/Setters 
    
    public InputAction getInputAction() {
        return inputAction;
    }

    public List<Process> getEntities() {
        return entities;
    }

    public List<Process> getFilteredEntities() {
        return filteredEntities;
    }

    public void setFilteredEntities(List<Process> filteredEntities) {
        this.filteredEntities = filteredEntities;
    }

    public Process getInputEntity() {
        return inputEntity;
    }

    public void setInputEntity(Process inputEntity) {
        this.inputEntity = inputEntity;
    }

    public Process getSelectedEntity() {
        return selectedEntity;
    }

    public void setSelectedEntity(Process selectedEntity) {
        this.selectedEntity = selectedEntity;
    }

    public List<Checklist> getStatusTypes() {
        return statusTypes;
    }   

    public ChecklistEJB getLcEJB() {
        return lcEJB;
    }

    public void setLcEJB(ChecklistEJB lcEJB) {
        this.lcEJB = lcEJB;
    }

    public String getSelectedType() {
        return selectedType;
    }

    public void setSelectedType(String selectedType) {
        this.selectedType = selectedType;
    }
}
