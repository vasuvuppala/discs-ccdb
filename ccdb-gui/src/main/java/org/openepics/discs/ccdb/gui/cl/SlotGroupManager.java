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
import javax.inject.Inject;
import org.openepics.discs.ccdb.core.auth.LocalAuthEJB;
import org.openepics.discs.ccdb.core.ejb.ChecklistEJB;
import org.openepics.discs.ccdb.core.security.SecurityPolicy;
import org.openepics.discs.ccdb.gui.ui.util.UiUtility;
import org.openepics.discs.ccdb.model.auth.AuthUser;
import org.openepics.discs.ccdb.model.cl.SlotGroup;

import org.primefaces.context.RequestContext;
import org.primefaces.event.SelectEvent;

/**
 * Description: State for Manage Process View
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
public class SlotGroupManager implements Serializable {
    @EJB
    private ChecklistEJB lcEJB;
    @EJB
    private LocalAuthEJB authEJB;            
//    @Inject
//    private SecurityPolicy securityPolicy;
     
    private static final Logger LOGGER = Logger.getLogger(SlotGroupManager.class.getName());
//    @Inject
//    UserSession userSession;
      
    private List<SlotGroup> entities;    
    private List<SlotGroup> filteredEntities;    
    private SlotGroup inputEntity;
    private SlotGroup selectedEntity;
    private InputAction inputAction;
    private AuthUser currentUser;
    
    public SlotGroupManager() {
    }
    
    @PostConstruct
    public void init() {      
        entities = lcEJB.findAllSlotGroups(); 

        resetInput();
    }
    
    private void resetInput() {                
        inputAction = InputAction.READ;
    }
    
    public void onRowSelect(SelectEvent event) {
        // inputRole = selectedRole;
        // Utility.showMessage(FacesMessage.SEVERITY_INFO, "Role Selected", "");
    }
    
    public void onAddCommand(ActionEvent event) {
        inputEntity = new SlotGroup();
        inputAction = InputAction.CREATE;       
    }
    
    public void onEditCommand(ActionEvent event) {
        inputAction = InputAction.UPDATE;
    }
    
    public void onDeleteCommand(ActionEvent event) {
        inputAction = InputAction.DELETE;
    }
    
    private Boolean isAuthorized() {
         currentUser = authEJB.getCurrentUser();
        if (currentUser == null) {         
            UiUtility.showMessage(FacesMessage.SEVERITY_ERROR, "Update Failed",
                    "You are not authorized. User Id is null.");
            RequestContext.getCurrentInstance().addCallbackParam("success", false);
            return false;
        }
       
        return true;
    }
    
    public void saveEntity() {
        try {                      
            if (! isAuthorized()) {
                UiUtility.showMessage(FacesMessage.SEVERITY_ERROR, "Unauthorized", "You are not authorized to perform this action");
                return;
            }
            if (inputAction == InputAction.CREATE) {
                inputEntity.setOwner(currentUser);
                lcEJB.saveSlotGroup(inputEntity);
                entities.add(inputEntity);                
            } else {
                selectedEntity.setOwner(currentUser);
                lcEJB.saveSlotGroup(selectedEntity);
                lcEJB.refreshVersion(SlotGroup.class, selectedEntity);
            }
            resetInput();
            RequestContext.getCurrentInstance().addCallbackParam("success", true);
            UiUtility.showMessage(FacesMessage.SEVERITY_INFO, "Saved", "");
        } catch (Exception e) {
            UiUtility.showMessage(FacesMessage.SEVERITY_ERROR, "Could not save ", e.getMessage());
            RequestContext.getCurrentInstance().addCallbackParam("success", false);
            System.out.println(e);
        }
    }
    
    public void deleteEntity() {
        try {
            lcEJB.deleteSlotGroup(selectedEntity);
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

    public List<SlotGroup> getEntities() {
        return entities;
    }

    public List<SlotGroup> getFilteredEntities() {
        return filteredEntities;
    }

    public void setFilteredEntities(List<SlotGroup> filteredEntities) {
        this.filteredEntities = filteredEntities;
    }

    public SlotGroup getInputEntity() {
        return inputEntity;
    }

    public void setInputEntity(SlotGroup inputEntity) {
        this.inputEntity = inputEntity;
    }

    public SlotGroup getSelectedEntity() {
        return selectedEntity;
    }

    public void setSelectedEntity(SlotGroup selectedEntity) {
        this.selectedEntity = selectedEntity;
    }
}